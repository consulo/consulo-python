// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.RemoveUnnecessaryBackslashQuickFix;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.Stack;
import org.jspecify.annotations.Nullable;

/**
 * User: catherine
 * <p/>
 * Inspection to highlight backslashes in places where line continuation is implicit (inside (), [], {}).
 */
@ExtensionImpl
public final class PyUnnecessaryBackslashInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnnecessaryBackslash();
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

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyParameterList(PyParameterList list) {
            findProblem(list);
        }

        @Override
        public void visitPyArgumentList(PyArgumentList list) {
            findProblem(list);
        }

        @Override
        public void visitPyTupleExpression(PyTupleExpression node) {
            if (node.getParent() instanceof PyParenthesizedExpression) {
                findProblem(node);
            }
        }

        @Override
        public void visitPyParenthesizedExpression(PyParenthesizedExpression expression) {
            Stack<PsiElement> stack = new Stack<>();
            stack.push(expression);
            while (!stack.isEmpty()) {
                PsiElement element = stack.pop();
                if (!(element instanceof PyTupleExpression)) {
                    findProblem(element);
                    if (element != null) {
                        for (PsiElement psiElement : element.getChildren()) {
                            stack.push(psiElement);
                        }
                    }
                }
            }
        }

        @Override
        public void visitPyDictLiteralExpression(PyDictLiteralExpression expression) {
            findProblem(expression);
        }

        @Override
        public void visitPyListLiteralExpression(PyListLiteralExpression expression) {
            findProblem(expression);
        }

        @Override
        public void visitPySetLiteralExpression(PySetLiteralExpression expression) {
            findProblem(expression);
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression stringLiteralExpression) {
            PsiElement parent = stringLiteralExpression.getParent();
            if (parent instanceof PyListLiteralExpression || parent instanceof PyParenthesizedExpression ||
                parent instanceof PySetLiteralExpression || parent instanceof PyKeyValueExpression ||
                parent instanceof PyNamedParameter || parent instanceof PyArgumentList) {
                findProblem(stringLiteralExpression);
            }
        }

        private void findProblem(@Nullable PsiElement expression) {
            PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(expression, PsiWhiteSpace.class);
            if (children != null) {
                for (PsiWhiteSpace ws : children) {
                    if (ws.getText().contains("\\")) {
                        registerProblem(ws, PyLocalize.inspUnnecessaryBackslashUnnecessaryBackslashInExpression().get(),
                                        new RemoveUnnecessaryBackslashQuickFix());
                    }
                }
            }
        }
    }
}
