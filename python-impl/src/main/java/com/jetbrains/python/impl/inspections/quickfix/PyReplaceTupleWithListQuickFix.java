// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyReplaceTupleWithListQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameMakeList();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        assert element instanceof PyAssignmentStatement;
        PyExpression[] targets = ((PyAssignmentStatement)element).getTargets();
        if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression subscriptionExpression) {
            if (subscriptionExpression.getOperand() instanceof PyReferenceExpression referenceExpression) {
                TypeEvalContext context = TypeEvalContext.userInitiated(project, element.getContainingFile());
                PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
                element = referenceExpression.followAssignmentsChain(resolveContext).getElement(); // Consulo: no ModPsiUpdater.getWritable, fix runs on the physical file
                if (element instanceof PyParenthesizedExpression) {
                    PyExpression expression = ((PyParenthesizedExpression)element).getContainedExpression();
                    replaceWithListLiteral(element, (PyTupleExpression)expression);
                }
                else if (element instanceof PyTupleExpression) {
                    replaceWithListLiteral(element, (PyTupleExpression)element);
                }
            }
        }
    }

    private static void replaceWithListLiteral(PsiElement element, PyTupleExpression expression) {
        String expressionText = expression.isEmpty() ? "" : expression.getText();
        PyExpression literal = PyElementGenerator.getInstance(element.getProject()).
            createExpressionFromText(LanguageLevel.forElement(element),
                                     "[" + expressionText + "]");
        element.replace(literal);
    }
}
