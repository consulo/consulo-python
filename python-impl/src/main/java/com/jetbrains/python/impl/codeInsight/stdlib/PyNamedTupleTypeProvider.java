// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.impl.psi.types.PyNamedTupleType;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl(id = "pyNamedTupleTypeProvider", order = "after pyDecoratedFunctionTypeProvider")
public class PyNamedTupleTypeProvider extends PyTypeProviderBase {

    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
        PyCallExpression callAnchor = anchor != null ? PyCallExpressionNavigator.getPyCallExpressionByCallee(anchor) : null;
        PyType type;
        if (referenceTarget instanceof PyFunction function && callAnchor != null) {
            type = PyNamedTupleTypeProviderKt.getNamedTupleFunctionType(function, context, callAnchor);
        }
        else if (referenceTarget instanceof PyTargetExpression targetExpression) {
            type = PyNamedTupleTypeProviderKt.getNamedTupleTypeForTarget(targetExpression, context);
        }
        else if (referenceTarget instanceof PyClass pyClass && callAnchor != null) {
            type = PyNamedTupleTypeProviderKt.getNamedTupleTypeForClass(pyClass, context, callAnchor);
        }
        else {
            type = null;
        }
        return PyTypeUtil.notNullToRef(type);
    }

    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
        if (!param.isSelf()) {
            return null;
        }
        PyClass cls = func.getContainingClass();
        if (cls == null) {
            return null;
        }
        PyNamedTupleType type = PyNamedTupleTypeProviderKt.getNamedTupleTypeForClass(cls, context);
        if (type == null) {
            return null;
        }
        PyNamedTupleType scopeClassType = func.getModifier() == PyAstFunction.Modifier.CLASSMETHOD ? type.toClass() : type.toInstance();
        return Ref.create(new PySelfType(scopeClassType));
    }

    @Override
    public @Nullable PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        PyType type = PyNamedTupleTypeProviderKt.getFieldTypeForNamedTupleAsTarget(referenceExpression, context);
        if (type != null) {
            return type;
        }
        type = PyNamedTupleTypeProviderKt.getFieldTypeForTypingNTFunctionInheritor(referenceExpression, context);
        if (type != null) {
            return type;
        }
        return PyNamedTupleTypeProviderKt.getNamedTupleReplaceType(referenceExpression, context);
    }

    @Override
    public @Nullable Ref<@Nullable PyType> prepareCalleeTypeForCall(@Nullable PyType type, PyExpression callee, TypeEvalContext context) {
        if (!(type instanceof PyNamedTupleType namedTupleType)) {
            return null;
        }
        PyCallableType constructorType = PyNamedTupleTypeProviderKt.generateGenericConstructorType(namedTupleType, context);
        return Ref.create(constructorType != null ? constructorType : namedTupleType);
    }

    @Override
    public @Nullable List<PyTypeMember> getMemberTypes(PyType type,
                                                       String name,
                                                       @Nullable PyExpression location,
                                                       AccessDirection direction,
                                                       PyResolveContext context) {
        TypeEvalContext typeEvalContext = context.getTypeEvalContext();
        if (PyNames.MATCH_ARGS.equals(name) && Helper.isNamedTuple(type, typeEvalContext)) {
            PyClassType classType = (PyClassType) type;
            PyCallableType callableType = PyNamedTupleTypeProviderKt.getCallableType(classType, typeEvalContext, classType.getPyClass());
            List<PyCallableParameter> parameters = callableType != null ? callableType.getParameters(typeEvalContext) : null;
            if (parameters == null) {
                return null;
            }
            List<String> fieldNames = new ArrayList<>();
            for (PyCallableParameter it : parameters) {
                String parameterName = it.getName();
                if (parameterName != null) {
                    fieldNames.add(parameterName);
                }
            }
            PyType matchArgsType = PyTypeUtil.createTupleOfLiteralStringsType(classType.getPyClass(), fieldNames);
            if (matchArgsType == null) {
                return null;
            }
            return List.of(new PyTypeMember((PsiElement) null, matchArgsType));
        }
        if (!(type instanceof PyNamedTupleType namedTupleType)) {
            return null;
        }
        PyNamedTupleType.FieldTypeAndDefaultValue field = namedTupleType.getFields().get(name);
        if (field != null) {
            return List.of(new PyTypeMember((PsiElement) null, field.getType()));
        }
        return null;
    }

    public static final class Helper {
        private Helper() {
        }

        public static boolean isNamedTuple(@Nullable PyType type, TypeEvalContext context) {
            if (type instanceof PyNamedTupleType) {
                return true;
            }

            if (!(type instanceof PyClassType classType)) {
                return false;
            }
            for (PyClassLikeType t : classType.getAncestorTypes(context)) {
                if (t instanceof PyNamedTupleType || t != null && PyTypingTypeProvider.NAMEDTUPLE.equals(t.getClassQName())) {
                    return true;
                }
            }
            return false;
        }

        public static boolean isTypingNamedTupleDirectInheritor(PyClass cls, TypeEvalContext context) {
            for (PyClassLikeType type : cls.getSuperClassTypes(context)) {
                if (type != null && !(type instanceof PyNamedTupleType) && PyTypingTypeProvider.NAMEDTUPLE.equals(type.getClassQName())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns true if {@code attribute} is a field of a class that inherits directly from typing `NamedTuple`.
         * A field has an annotation and is not a `ClassVar`.
         * An instance also cannot change an unannotated class attribute, but this function does not include it.
         */
        // @ApiStatus.Internal
        public static boolean isNamedTupleField(PyTargetExpression attribute, TypeEvalContext context) {
            if (attribute.getAnnotationValue() == null ||
                !PyUtil.isClassAttribute(attribute) ||
                PyTypingTypeProvider.isClassVar(attribute, context)) {
                return false;
            }
            PyClass cls = attribute.getContainingClass();
            if (cls == null) {
                return false;
            }
            return isTypingNamedTupleDirectInheritor(cls, context);
        }
    }
}
