// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.ListCreationQuickFix;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
  * User :catherine
  */
@ExtensionImpl
public final class PyListCreationInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameListCreation();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
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
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            if (!(node.getAssignedValue() instanceof PyListLiteralExpression)) {
                return;
            }
            PyExpression[] targets = node.getTargets();
            if (targets.length != 1) {
                return;
            }
            PyExpression target = targets[0];
            String name = target.getName();
            if (name == null) {
                return;
            }
            List<PyExpressionStatement> appendCalls = collectSubsequentListAppendCalls(node);
            if (!appendCalls.isEmpty()) {
                registerProblem(node, PyLocalize.inspListCreationThisListCreationCouldBeRewrittenAsListLiteral().get(),
                                new ListCreationQuickFix());
            }
        }
    }

    public static List<PyExpressionStatement> collectSubsequentListAppendCalls(PyAssignmentStatement assignment) {
        ArrayList<PyExpressionStatement> result = new ArrayList<>();
        PyExpression[] targets = assignment.getTargets();
        assert targets.length == 1;
        PyExpression target = targets[0];
        String name = target.getName();
        assert name != null;
        PyStatement expressionStatement = PsiTreeUtil.getNextSiblingOfType(assignment, PyStatement.class);
        while (expressionStatement instanceof PyExpressionStatement) {
            PyExpression statement = ((PyExpressionStatement)expressionStatement).getExpression();
            if (!(statement instanceof PyCallExpression callExpression)) {
                break;
            }

            PyExpression callee = callExpression.getCallee();
            if (!(callee instanceof PyQualifiedExpression)) {
                break;
            }

            PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
            if (qualifier == null || !name.equals(qualifier.getText())) {
                break;
            }

            String funcName = ((PyQualifiedExpression)callee).getReferencedName();
            if (!"append".equals(funcName)) {
                break;
            }

            PyArgumentList argList = callExpression.getArgumentList();
            if (argList != null) {
                // TODO Use proper resolve to the original target here
                if (ContainerUtil.exists(argList.getArguments(), argument -> argument.getText().equals(name))) {
                    break;
                }
                result.add((PyExpressionStatement)expressionStatement);
            }
            expressionStatement = PsiTreeUtil.getNextSiblingOfType(expressionStatement, PyStatement.class);
        }
        return result;
    }
}
