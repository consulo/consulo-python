// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyABCUtil;
import com.jetbrains.python.impl.psi.types.PyLiteralStringType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyNoneTypeKt;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithAncestors;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionCondition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.util.lang.StringUtil;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public interface PyPostfixTemplateExpressionCondition extends PostfixTemplateExpressionCondition<PyExpression> {
    @Override
    boolean equals(@Nullable Object other);

    @Override
    int hashCode();

    abstract class PySimpleConditionBase implements PyPostfixTemplateExpressionCondition {
        public final String id;

        protected PySimpleConditionBase(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other != null && getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }

    class PyBooleanExpression extends PySimpleConditionBase {
        private static final String ID = "boolean";

        public PyBooleanExpression() {
            super(ID);
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionBooleanName().get();
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType type = PyLiteralType.upcastLiteralToClass(context.getType(element));
            if (type == null) {
                return false;
            }
            return Objects.equals(PyBuiltinCache.getInstance(element).getBoolType(), type);
        }
    }

    class PyNumberExpression extends PySimpleConditionBase {
        private static final String ID = "number";

        public PyNumberExpression() {
            super(ID);
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionNumberName().get();
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType type = PyLiteralType.upcastLiteralToClass(context.getType(element));
            if (type == null) {
                return false;
            }
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
            PyClassType intType = builtinCache.getIntType();
            PyClassType floatType = builtinCache.getFloatType();
            PyClassType complexType = builtinCache.getComplexType();
            return PyTypeChecker.match(
                PyUnionType.unionOrUnknown(Arrays.asList(intType, floatType, complexType)), type, context
            );
        }
    }

    class PyStringExpression extends PySimpleConditionBase {
        private static final String ID = "string";

        public PyStringExpression() {
            super(ID);
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionStringName().get();
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType type = PyLiteralType.upcastLiteralToClass(context.getType(element));
            if (type == null) {
                return false;
            }
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
            LanguageLevel languageLevel = LanguageLevel.forElement(element);
            Set<PyType> types = new LinkedHashSet<>();
            for (PyType t : Arrays.asList(builtinCache.getStringType(languageLevel), builtinCache.getByteStringType(languageLevel),
                builtinCache.getUnicodeType(languageLevel))) {
                if (t != null) {
                    types.add(t);
                }
            }
            if (types.contains(type) || type instanceof PyLiteralStringType) {
                return true;
            }
            for (PyType t : types) {
                if (t instanceof PyUnionType unionType && unionType.getMembers().contains(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    class PyIterable extends PySimpleConditionBase {
        private static final String ID = "iterable";

        public PyIterable() {
            super(ID);
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionIterableName().get();
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType type = context.getType(element);
            if (type == null) {
                return false;
            }
            return PyABCUtil.isSubtype(type, PyNames.ITERABLE, context);
        }
    }

    abstract class PyCollectionTypeConditionBase extends PySimpleConditionBase {
        private final String myType;

        protected PyCollectionTypeConditionBase(String type) {
            super(type);
            myType = type;
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType elementType = context.getType(element);
            if (elementType == null) {
                return false;
            }
            if (elementType instanceof PyClassLikeType classLikeType) {
                return Objects.equals(PyNames.FQN.unqualifyBuiltinName(classLikeType.getClassQName()), myType) ||
                    Helper.inherits(classLikeType, context, myType);
            }
            return false;
        }
    }

    class PyDict extends PyCollectionTypeConditionBase {
        public PyDict() {
            super("dict");
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionDictName().get();
        }
    }

    class PyList extends PyCollectionTypeConditionBase {
        public PyList() {
            super("list");
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionListName().get();
        }
    }

    class PySet extends PyCollectionTypeConditionBase {
        public PySet() {
            super("set");
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionSetName().get();
        }
    }

    class PyTuple extends PyCollectionTypeConditionBase {
        public PyTuple() {
            super("tuple");
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionTupleName().get();
        }
    }

    class PyNonNoneExpression extends PySimpleConditionBase {
        private static final String ID = "non none";

        public PyNonNoneExpression() {
            super(ID);
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionNonNoneName().get();
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType type = context.getType(element);
            if (type == null) {
                return false;
            }
            return !PyNoneTypeKt.isNoneType(type);
        }
    }

    class PyExceptionExpression extends PySimpleConditionBase {
        private static final String ID = "exception";

        public PyExceptionExpression() {
            super(ID);
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionExceptionName().get();
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType type = context.getType(element);
            if (type == null) {
                return false;
            }
            if (!(type instanceof PyClassType classType)) {
                return false;
            }
            return PyUtil.isExceptionClass(classType.getPyClass());
        }
    }

    class PyBuiltinLenApplicable extends PySimpleConditionBase {
        private static final String ID = "builtin len applicable";

        public PyBuiltinLenApplicable() {
            super(ID);
        }

        @Override
        public String getPresentableName() {
            return PyCodeInsightLocalize.postfixTemplateConditionBuiltinLenApplicableName().get();
        }

        @Override
        public boolean test(PyExpression element) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
            PyType type = context.getType(element);
            if (type == null) {
                return false;
            }
            return PyABCUtil.isSubtype(type, PyNames.SIZED, context);
        }
    }

    final class PyClassCondition extends PySimpleConditionBase {
        public static final String ID = "type";
        public static final String TYPE_ATTR = "type";

        private final QualifiedName myName;

        public PyClassCondition(QualifiedName name) {
            super(ID);
            myName = name;
        }

        @Override
        public String getPresentableName() {
            return myName.toString();
        }

        @Override
        public boolean test(PyExpression element) {
            PyExpression expression = element;
            if (!element.getContainingFile().isPhysical()) {
                // Template engine creates a copy of the psi file and uses an element from the copy.
                // We need to use the original expression because type compatibility check relies
                // on `PsiElement.isEquivalentTo` which fails if a type of the expression is defined
                // in the same file as an expression (we get unequal (!=) objects after resolve: the desired
                // type is in the real file, and the expressionType is not).
                PsiElement at = CompletionUtilCore.getOriginalElement(element);
                expression = PsiTreeUtil.getParentOfType(at, PyExpression.class, false);
            }
            if (expression == null) {
                return false;
            }
            TypeEvalContext context = TypeEvalContext.codeCompletion(expression.getProject(), expression.getContainingFile());
            PyType expressionType = context.getType(expression);
            if (expressionType == null) {
                return false;
            }
            if (expressionType instanceof PyClassLikeType classLikeType) {
                String expected = myName.toString();
                return Objects.equals(PyNames.FQN.unqualifyBuiltinName(classLikeType.getClassQName()),
                    PyNames.FQN.unqualifyBuiltinName(expected)) ||
                    Helper.inherits(classLikeType, context, expected);
            }
            return false;
        }

        @Override
        public void serializeTo(Element element) {
            super.serializeTo(element);
            element.setAttribute(TYPE_ATTR, myName.toString());
        }

        // data class semantics
        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof PyClassCondition that && myName.equals(that.myName);
        }

        @Override
        public int hashCode() {
            return myName.hashCode();
        }

        @Override
        public String toString() {
            return "PyClassCondition(name=" + myName + ")";
        }

        public static @Nullable PyClassCondition readFrom(Element element) {
            String value = element.getAttributeValue(TYPE_ATTR);
            return value != null ? new PyClassCondition(QualifiedName.fromDottedString(value)) : null;
        }

        public static @Nullable PyClassCondition create(PyClass typeSpec) {
            String name = typeSpec.getQualifiedName();
            if (StringUtil.isEmpty(name)) {
                return null;
            }
            return new PyClassCondition(QualifiedName.fromDottedString(name));
        }

        public static @Nullable PyClassCondition create(@Nullable String typeName) {
            if (StringUtil.isEmpty(typeName)) {
                return null;
            }
            return new PyClassCondition(QualifiedName.fromDottedString(typeName));
        }
    }

    // conditions we allow to select in postfix template editor UI
    Map<String, PyPostfixTemplateExpressionCondition> PUBLIC_CONDITIONS = Helper.getConditionsMap(
        new PyBooleanExpression(),
        new PyNumberExpression(),
        new PyStringExpression(),
        new PyIterable(),
        new PyDict(),
        new PyList(),
        new PySet(),
        new PyTuple(),
        new PyNonNoneExpression(),
        new PyExceptionExpression()
    );

    final class Helper {
        private Helper() {
        }

        private static Map<String, PyPostfixTemplateExpressionCondition> getConditionsMap(PyPostfixTemplateExpressionCondition... conditions) {
            Map<String, PyPostfixTemplateExpressionCondition> result = new LinkedHashMap<>();
            for (PyPostfixTemplateExpressionCondition condition : conditions) {
                result.put(condition.getId(), condition);
            }
            return Collections.unmodifiableMap(result);
        }

        private static boolean inherits(PyWithAncestors type, TypeEvalContext evalContext, String... parentNames) {
            // Unqualify on both sides so short builtin names (e.g. "list") still match ancestors whose
            // qualified name is now "builtins.list".
            Set<String> names = new HashSet<>();
            for (String parentName : parentNames) {
                String unqualified = PyNames.FQN.unqualifyBuiltinName(parentName);
                if (unqualified != null) {
                    names.add(unqualified);
                }
            }
            List<@Nullable PyClassLikeType> ancestors = type.getAncestorTypes(evalContext);
            for (PyClassLikeType ancestor : ancestors) {
                if (ancestor == null) {
                    continue;
                }
                String unqualified = PyNames.FQN.unqualifyBuiltinName(ancestor.getClassQName());
                if (unqualified != null && names.contains(unqualified)) {
                    return true;
                }
            }
            return false;
        }
    }
}
