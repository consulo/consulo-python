// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceFunctionWithSetLiteralQuickFix;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * User: catherine
 * <p>
 * Inspection to find set built-in function and replace it with set literal
 * available if the selected language level supports set literals.
 */
@ExtensionImpl
public final class PySetFunctionToLiteralInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameSetFunctionToLiteral();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
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
        public void visitPyCallExpression(PyCallExpression node) {
            PyExpression callee = node.getCallee();
            if (node.isCalleeText(PyNames.SET) && callee != null && PyBuiltinCache.isInBuiltins(callee)) {
                PyExpression[] arguments = node.getArguments();
                if (arguments.length == 1) {
                    PyElement[] elements = getSetCallArguments(node);
                    if (elements.length != 0) {
                        registerProblem(node, PyLocalize.inspNameSetFunctionToLiteral().get(),
                                        new ReplaceFunctionWithSetLiteralQuickFix());
                    }
                }
            }
        }
    }

    public static PyElement[] getSetCallArguments(PyCallExpression node) {
        PyExpression argument = node.getArguments()[0];
        if (argument instanceof PyStringLiteralExpression) {
            return PyElement.EMPTY_ARRAY;
        }
        if ((argument instanceof PySequenceExpression || (argument instanceof PyParenthesizedExpression &&
                                                          ((PyParenthesizedExpression)argument).getContainedExpression() instanceof PyTupleExpression))) {

            if (argument instanceof PySequenceExpression) {
                return ((PySequenceExpression)argument).getElements();
            }
            PyExpression tuple = ((PyParenthesizedExpression)argument).getContainedExpression();
            if (tuple instanceof PyTupleExpression) {
                return ((PyTupleExpression)(tuple)).getElements();
            }
        }
        return PyElement.EMPTY_ARRAY;
    }
}
