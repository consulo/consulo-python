// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.util.collection.ContainerUtil;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.inspections.quickfix.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: catherine
 * <p>
 * Inspection to detect situations, where argument passed to function
 * is equal to default parameter value
 * for instance,
 * dict().get(x, None) --> None is default value for second param in dict().get function
 */
@ExtensionImpl
public final class PyArgumentEqualDefaultInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameArgumentEqualDefault();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
    }


    @Override
    public boolean isEnabledByDefault() {
        return false;
    }


    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }


    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            if (node.getParent() instanceof PyDecorator) {
                return;
            }
            List<PyCallable> callables = node.multiResolveCalleeFunction(getResolveContext());
            if (ContainerUtil.exists(callables, callable -> hasSpecialCasedDefaults(callable, node))) {
                return;
            }
            checkArguments(node, node.getArguments());
        }

        @Override
        public void visitPyDecorator(PyDecorator decorator) {
            if (decorator.hasArgumentList()) {
                PyExpression[] arguments = decorator.getArguments();
                checkArguments(decorator, arguments);
            }
        }

        private static boolean hasSpecialCasedDefaults(PyCallable callable, PsiElement anchor) {
            String name = callable.getName();
            PyBuiltinCache cache = PyBuiltinCache.getInstance(anchor);
            if ("getattr".equals(name) && cache.isBuiltin(callable)) {
                return true;
            }
            else if ("get".equals(name) || "pop".equals(name)) {
                PyFunction method = callable.asMethod();
                PyClassType dictType = cache.getDictType();
                if (method != null && dictType != null && method.getContainingClass() == dictType.getPyClass()) {
                    return true;
                }
            }
            return false;
        }

        private void checkArguments(PyCallExpression callExpr, PyExpression[] arguments) {
            PyCallExpression.PyArgumentsMapping mapping = ContainerUtil.getFirstItem(callExpr.multiMapArguments(getResolveContext()));
            if (mapping == null) {
                return;
            }
            Set<PyExpression> problemElements = new HashSet<>();
            for (Map.Entry<PyExpression, PyCallableParameter> e : mapping.getMappedParameters().entrySet()) {
                PyExpression defaultValue = findDefaultValue(mapping, e.getValue());
                if (defaultValue != null) {
                    PyExpression key = e.getKey();
                    if (key instanceof PyKeywordArgument && ((PyKeywordArgument)key).getValueExpression() != null) {
                        key = ((PyKeywordArgument)key).getValueExpression();
                    }
                    if (isEqual(key, defaultValue)) {
                        problemElements.add(e.getKey());
                    }
                }
            }
            boolean canDelete = true;
            for (int i = arguments.length - 1; i != -1; --i) {
                if (problemElements.contains(arguments[i])) {
                    if (canDelete) {
                        registerProblem(arguments[i], PyLocalize.inspArgumentEqualsToDefault().get(),
                                        new RemoveArgumentEqualDefaultQuickFix(problemElements));
                    }
                    else {
                        registerProblem(arguments[i], PyLocalize.inspArgumentEqualsToDefault().get());
                    }
                }
                else if (!(arguments[i] instanceof PyKeywordArgument)) {
                    canDelete = false;
                }
            }
        }

        private static @Nullable PyExpression findDefaultValue(PyCallExpression.PyArgumentsMapping mapping,
                                                               PyCallableParameter parameter) {
            String name = parameter.getName();
            PyExpression value = parameter.getDefaultValue();

            if (name == null || !(value instanceof PyEllipsisLiteralExpression)) {
                return value;
            }

            PyCallableType callableType = mapping.getCallableType();
            if (callableType == null) {
                return value;
            }

            PyCallable callable = callableType.getCallable();
            if (callable == null) {
                return value;
            }

            if (PyiUtil.isInsideStub(callable)) {
                PyCallable originalCallable = PyiUtil.getOriginalElementOrLeaveAsIs(callable, PyCallable.class);

                PyNamedParameter originalParameter = originalCallable.getParameterList().findParameterByName(name);
                if (originalParameter != null) {
                    return originalParameter.getDefaultValue();
                }
            }

            return value;
        }

        private boolean isEqual(PyExpression key, PyExpression defaultValue) {
            if (isBothInstanceOf(key, defaultValue, PyNumericLiteralExpression.class) ||
                isBothInstanceOf(key, defaultValue, PyPrefixExpression.class) ||
                isBothInstanceOf(key, defaultValue, PyBinaryExpression.class) ||
                isBothInstanceOf(key, defaultValue, PyNoneLiteralExpression.class) ||
                isBothInstanceOf(key, defaultValue, PyBoolLiteralExpression.class)) {
                if (key.getText().equals(defaultValue.getText())) {
                    return true;
                }
            }
            else if (key instanceof PyStringLiteralExpression && defaultValue instanceof PyStringLiteralExpression) {
                if (((PyStringLiteralExpression)key).getStringValue().equals(((PyStringLiteralExpression)defaultValue).getStringValue())) {
                    return true;
                }
            }
            else if (key instanceof PyReferenceExpression && PyUtil.isPy2ReservedWord((PyReferenceExpression)key) &&
                     key.getText().equals(defaultValue.getText())) {
                return true;
            }
            else {
                PsiReference keyRef = key instanceof PyReferenceExpression
                                      ? ((PyReferenceExpression)key).getReference(getResolveContext())
                                      : key.getReference();
                PsiReference defRef = defaultValue instanceof PyReferenceExpression
                                      ? ((PyReferenceExpression)defaultValue).getReference(getResolveContext())
                                      : defaultValue.getReference();
                if (keyRef != null && defRef != null) {
                    PsiElement keyResolve = keyRef.resolve();
                    PsiElement defResolve = defRef.resolve();
                    if (keyResolve != null && keyResolve.equals(defResolve)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean isBothInstanceOf(PyExpression key,
                                                PyExpression defaultValue,
                                                Class clazz) {
            return clazz.isInstance(key) && clazz.isInstance(defaultValue);
        }
    }
}
