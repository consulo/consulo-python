// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.PyMakeFunctionFromMethodQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyMakeMethodStaticQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
  * User: ktisha
  *
  */
@ExtensionImpl
public final class PyMethodMayBeStaticInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMethodMayBeStatic();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonClassStructure();
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
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            if (isBuiltin(node)) {
                return;
            }
            PyClass containingClass = node.getContainingClass();
            if (containingClass == null) {
                return;
            }
            PsiElement firstSuper = PySuperMethodsSearch.search(node, myTypeEvalContext).findFirst();
            if (firstSuper != null) {
                return;
            }
            PyFunction firstOverride = PyOverridingMethodsSearch.search(node, true).findFirst();
            if (firstOverride != null) {
                return;
            }
            PyDecoratorList decoratorList = node.getDecoratorList();
            if (decoratorList != null) {
                return;
            }
            if (node.getModifier() != null) {
                return;
            }
            Property property = containingClass.findPropertyByCallable(node);
            if (property != null) {
                return;
            }
            List<PyAssignmentStatement> attributes = node.findAttributes();
            if (!attributes.isEmpty()) {
                return;
            }
            if (isTestElement(node)) {
                return;
            }

            if (PyUtil.isEmptyFunction(node)) {
                return;
            }

            PyParameter[] parameters = node.getParameterList().getParameters();

            String selfName;
            if (parameters.length > 0) {
                String name = parameters[0].getName();
                selfName = name != null ? name : parameters[0].getText();
            }
            else {
                selfName = PyNames.CANONICAL_SELF;
            }

            boolean[] mayBeStatic = {true};
            PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
                @Override
                public void visitPyRaiseStatement(PyRaiseStatement node) {
                    super.visitPyRaiseStatement(node);
                    PyExpression[] expressions = node.getExpressions();
                    if (expressions.length == 1) {
                        PyExpression expression = expressions[0];
                        if (expression instanceof PyCallExpression) {
                            PyExpression callee = ((PyCallExpression)expression).getCallee();
                            if (callee != null && PyNames.NOT_IMPLEMENTED_ERROR.equals(callee.getText())) {
                                mayBeStatic[0] = false;
                            }
                        }
                        else if (PyNames.NOT_IMPLEMENTED_ERROR.equals(expression.getText())) {
                            mayBeStatic[0] = false;
                        }
                    }
                }

                @Override
                public void visitPyReferenceExpression(PyReferenceExpression node) {
                    if (node.isQualified()) {
                        super.visitPyReferenceExpression(node);
                    }
                    else if (selfName.equals(node.getName())) {
                        mayBeStatic[0] = false;
                    }
                }

                @Override
                public void visitPyCallExpression(PyCallExpression node) {
                    super.visitPyCallExpression(node);
                    if (!LanguageLevel.forElement(node).isPython2() && node.isCalleeText(PyNames.SUPER)) {
                        mayBeStatic[0] = false;
                    }
                }
            };
            node.accept(visitor);
            PsiElement identifier = node.getNameIdentifier();
            if (mayBeStatic[0] && identifier != null) {
                registerProblem(identifier, PyLocalize.inspMethodMayBeStatic().get(), ProblemHighlightType.WEAK_WARNING,
                                null, new PyMakeMethodStaticQuickFix(), new PyMakeFunctionFromMethodQuickFix());
            }
        }
    }

    private static boolean isBuiltin(PyFunction node) {
        String name = node.getName();
        return name != null && PyNames.getBuiltinMethods(LanguageLevel.forElement(node)).containsKey(name);
    }

    private static boolean isTestElement(PyFunction node) {
        String methodName = node.getName();
        PyClass pyClass = node.getContainingClass();
        String className = pyClass == null ? null : pyClass.getName();

        return methodName != null && className != null
               && methodName.toLowerCase(Locale.getDefault()).startsWith("test")
               && className.toLowerCase(Locale.getDefault()).startsWith("test");
    }
}
