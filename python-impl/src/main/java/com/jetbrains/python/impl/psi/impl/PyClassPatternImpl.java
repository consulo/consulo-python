// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.util.lang.ref.SoftReference;
import consulo.language.ast.ASTNode;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.controlflow.PyTypeAssertionEvaluator;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static consulo.util.collection.ContainerUtil.getFirstItem;
import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.PyUtil.multiResolveTopPriority;

public class PyClassPatternImpl extends PyElementImpl implements PyClassPattern, PyCaptureContext {
    public PyClassPatternImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyClassPattern(this);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyType type = context.getType(getClassNameReference());
        if (type instanceof PyClassType classType) {

            PyClassType parameterized = PyTypingTypeProvider.parameterizeType(type, context);
            if (parameterized != null) {
                classType = parameterized;
            }

            PyType instanceType = classType.toInstance();
            PyType captureType = PyCaptureContext.getCaptureType(this, context);
            // TODO consulo: Ref.deref missing
            Ref<PyType> assertionType = PyTypeAssertionEvaluator.createAssertionType(captureType, instanceType, true, true, context);
            return assertionType != null ? assertionType.get() : null;
        }
        return PyAnyType.getUnknown();
    }

    @Override
    public boolean canExcludePatternType(TypeEvalContext context) {
        PyReferenceExpression refName = getClassNameReference();
        PsiElement resolved = getFirstItem(multiResolveTopPriority(refName.getReference()));
        if (!(resolved instanceof PyClass)) {
            // def foo(clz: type[Clz], x):
            //     match x:
            //         case clz():  # <-- can actually be a subclass of Clz, so doesn't subtract
            return false;
        }

        if (!(context.getType(refName) instanceof PyClassType classType)) {
            return false;
        }

        List<PyPattern> arguments = getArgumentList().getPatterns();
        @Nullable String classQName = classType.getClassQName();
        if (classQName != null && SPECIAL_BUILTINS.contains(PyNames.FQN.unqualifyBuiltinName(classQName))) {
            if (arguments.isEmpty()) {
                return true;
            }
            if (arguments.size() > 1) {
                return false;
            }
            return isExhaustive(arguments.getFirst(), context);
        }

        List<String> matchArgs = getMatchArgs(classType, context);
        for (int i = 0; i < arguments.size(); i++) {
            PyPattern member = arguments.get(i);
            if (member instanceof PyKeywordPattern keywordPattern) {
                if (getMemberType(classType, keywordPattern.getKeyword(), context) == null) {
                    return false;
                }
                PyPattern valuePattern = keywordPattern.getValuePattern();
                if (valuePattern != null && !isExhaustive(valuePattern, context)) {
                    return false;
                }
            }
            else {
                if (matchArgs == null) {
                    return false;
                }
                if (i >= matchArgs.size()) {
                    return false;
                }
                if (!isExhaustive(member, context)) {
                    return false;
                }
            }
        }
        PyType captureType = PyCaptureContext.getCaptureType(this, context);
        PyType patternType = context.getType(this);
        return PyTypeUtil.toStream(captureType).anyMatch(it -> PyTypeChecker.match(patternType, it, context));
    }

    @Override
    public @Nullable PyType getCaptureTypeForChild(PyPattern pattern, TypeEvalContext context) {
        pattern = as(PsiTreeUtil.findFirstParent(pattern, el -> getArgumentList() == el.getParent()), PyPattern.class);
        if (pattern == null) {
            return null;
        }

        if (pattern instanceof PyKeywordPattern keywordPattern) {
            if (context.getType(this) instanceof PyClassType classType) {
                return SoftReference.deref(getMemberType(classType, keywordPattern.getKeyword(), context));
            }
            return null;
        }

        List<PyPattern> arguments = getArgumentList().getPatterns();
        int index = arguments.indexOf(pattern);
        if (index < 0) {
            return null;
        }

        // capture type can be a union like: list[int] | list[str]
        return PyTypeUtil.toStream(context.getType(this)).map(type -> {
            if (type instanceof PyClassType classType) {
                @Nullable String classQName = classType.getClassQName();
                if (classQName != null && SPECIAL_BUILTINS.contains(PyNames.FQN.unqualifyBuiltinName(classQName))) {
                    if (index == 0) {
                        return classType;
                    }
                    return null;
                }

                List<String> matchArgs = getMatchArgs(classType, context);
                if (matchArgs == null || index >= matchArgs.size()) {
                    return null;
                }

                Ref<PyType> memberType = getMemberType(classType, matchArgs.get(index), context);
                if (memberType == null) {
                    return null;
                }

                return PyTypeChecker.substitute(memberType.get(), PyTypeChecker.unifyReceiver(classType, context), context);
            }
            return null;
        }).collect(PyTypeUtil.toUnion());
    }

    /**
     * Checks if the pattern covers its entire capture type and is itself exhaustive (e.g., not a non-literal value pattern).
     */
    public static boolean isExhaustive(PyPattern pattern, TypeEvalContext context) {
        if (!pattern.canExcludePatternType(context)) {
            return false;
        }

        PyType captureType = PyCaptureContext.getCaptureType(pattern, context);
        PyType patternType = context.getType(pattern);
        // For composite pattern components, we need to ensure that the component pattern covers its capture type fully
        // TODO consulo: Ref.deref missing
        Ref<PyType> assertionType = PyTypeAssertionEvaluator.createAssertionType(captureType, patternType, false, true, context);
        return (assertionType != null ? assertionType.get() : null) instanceof PyNeverType;
    }

    public static @Nullable List<String> getMatchArgs(PyClassType type, TypeEvalContext context) {
        Ref<PyType> memberTypeRef = getMemberType(type, PyNames.MATCH_ARGS, context);
        if (memberTypeRef != null) {
            List<String> matchArgs = PyTypeUtil.extractStringLiteralsFromTupleType(memberTypeRef.get());
            if (matchArgs != null) {
                return matchArgs;
            }
        }

        List<String> matchArgs = type.getPyClass().getOwnMatchArgs();
        if (matchArgs != null) {
            return matchArgs;
        }

        for (PyClassLikeType baseType : type.getSuperClassTypes(context)) {
            if (baseType instanceof PyClassType baseClassType) {
                List<String> inherited = getMatchArgs(baseClassType, context);
                if (inherited != null) {
                    return inherited;
                }
            }
        }

        return null;
    }

    @Nullable
    static Ref<PyType> getMemberType(PyType type, String name, TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<PyTypeMember> members = type.findMember(name, resolveContext);
        if (members.isEmpty()) {
            return null;
        }
        return Ref.create(PyUnionType.unionOrUnknown(ContainerUtil.map(members, PyTypeMember::getType)));
    }
}
