// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyNoneTypeKt;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyPossibleClassMember;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

@ExtensionImpl
public class PyUnhashableInspection extends PyInspection {
    private static final List<String> DICT_KEY_METHODS = List.of("get", "pop", "setdefault");
    private static final List<String> SET_ELEMENT_METHODS = List.of("add", "remove", "discard");

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnhashableElements();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        private final TypeEvalContext context;

        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
            this.context = context;
        }

        @Override
        @RequiredReadAction
        public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
            for (PyKeyValueExpression element : node.getElements()) {
                processDictKey(element.getKey());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyDictCompExpression(PyDictCompExpression node) {
            if (!(node.getResultExpression() instanceof PyKeyValueExpression expression)) {
                return;
            }
            processDictKey(expression.getKey());
        }

        @Override
        @RequiredReadAction
        public void visitPySetLiteralExpression(PySetLiteralExpression node) {
            for (PyExpression element : node.getElements()) {
                processSetElement(element);
            }
        }

        @Override
        @RequiredReadAction
        public void visitPySetCompExpression(PySetCompExpression node) {
            PyExpression expression = node.getResultExpression();
            if (expression == null) {
                return;
            }
            processSetElement(expression);
        }

        @Override
        @RequiredReadAction
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            for (PyExpression target : node.getTargets()) {
                if (target instanceof PySubscriptionExpression subscription && isDict(subscription.getOperand())) {
                    PyExpression indexExpression = subscription.getIndexExpression();
                    if (indexExpression == null) {
                        continue;
                    }
                    processDictKey(indexExpression);
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyCallExpression(PyCallExpression node) {
            PyExpression callee = node.getCallee();
            PyExpression callParent = callee instanceof PyQualifiedExpression qualifiedExpression ? qualifiedExpression.getQualifier() : null;
            PyExpression[] arguments = node.getArguments();
            if (arguments.length == 0) {
                return;
            }
            PyExpression firstArgument = arguments[0];

            if (node.isCalleeText(PyNames.HASH_FUNCTION)) {
                processHashArgument(firstArgument);
            }
            String calleeName = callee != null ? callee.getName() : null;
            if (calleeName != null && DICT_KEY_METHODS.contains(calleeName) && isDict(callParent)) {
                processDictKey(firstArgument);
            }
            if (calleeName != null && SET_ELEMENT_METHODS.contains(calleeName) && isSet(callParent)) {
                processSetElement(firstArgument);
            }
        }

        private boolean isDict(@Nullable PyExpression expression) {
            return PyNames.DICT.equals(getParameterizedClassName(expression));
        }

        private boolean isSet(@Nullable PyExpression expression) {
            return PyNames.SET.equals(getParameterizedClassName(expression));
        }

        private @Nullable String getParameterizedClassName(@Nullable PyExpression expression) {
            if (expression == null) {
                return null;
            }
            if (context.getType(expression) instanceof PyClassType type && type.isParameterized()) {
                return type.getName();
            }
            return null;
        }

        @RequiredReadAction
        private void processDictKey(@Nullable PyExpression element) {
            registerIfUnhashable(element, PyLocalize::inspDictKeyNotHashable);
        }

        @RequiredReadAction
        private void processSetElement(@Nullable PyExpression element) {
            registerIfUnhashable(element, PyLocalize::inspSetElementNotHashable);
        }

        @RequiredReadAction
        private void processHashArgument(@Nullable PyExpression element) {
            registerIfUnhashable(element, PyLocalize::inspArgumentNotHashable);
        }

        @RequiredReadAction
        private void registerIfUnhashable(@Nullable PyExpression element, Function<Object, LocalizeValue> message) {
            if (element == null) {
                return;
            }
            PyType type = context.getType(element);
            if (!isHashable(type)) {
                registerProblem(element, message.apply(String.valueOf(type != null ? type.getName() : null)).get());
            }
        }

        private boolean isHashable(@Nullable PyType type) {
            if (type instanceof PyTupleType tupleType) {
                return tupleType.getElementTypes().stream().allMatch(this::isHashable);
            }
            else if (type instanceof PyUnionType unionType) {
                return unionType.getMembers().stream().allMatch(this::isHashable);
            }
            else if (type instanceof PyClassType classType) {
                return isHashableClass(classType);
            }
            else {
                return true;
            }
        }

        private boolean isHashableClass(PyClassType classType) {
            if (classType.isDefinition()) {
                return true; // class objects are always hashable (type.__hash__)
            }

            PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(classType.getPyClass(), context);

            Boolean definesHash = definesMethod(classType, PyNames.HASH);
            if (definesHash == null && dataclassParameters != null &&
                (Boolean.TRUE.equals(dataclassParameters.getFrozen()) || dataclassParameters.getUnsafeHash())) {
                definesHash = true;
            }
            if (definesHash != null) {
                return definesHash;
            }

            // Python classes are hashable by default unless `__eq__` is defined
            Boolean definesEq = definesMethod(classType, PyNames.EQ);
            if (definesEq == null && dataclassParameters != null) {
                definesEq = dataclassParameters.getEq();
            }
            return !Boolean.TRUE.equals(definesEq);
        }

        /**
         * @return True if the class or its parent set this name to something other than None, False if to None,
         * null if the method fallbacks to `object`
         */
        private @Nullable Boolean definesMethod(PyClassType classType, String name) {
            List<PyTypeMember> members = classType.findMember(name, getResolveContext());
            if (members.isEmpty() || !(members.get(0).getElement() instanceof PyTypedElement member)) {
                return null;
            }

            PyClass containingClass = member instanceof PyPossibleClassMember possibleClassMember ? possibleClassMember.getContainingClass() : null;
            if (containingClass == null) {
                return null;
            }
            if (PyUtil.isObjectClass(containingClass)) {
                return null;
            }

            return !PyNoneTypeKt.isNoneType(context.getType(member));
        }
    }
}
