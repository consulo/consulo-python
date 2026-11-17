// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementImpl;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.List;

/**
 * User: catherine
 * <p>
 * QuickFix to replace assignment that can be replaced with augmented assignment.
 * for instance, i = i + 1   --> i +=1
 */
public class AugmentedAssignmentQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAugmentAssignment();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyAssignmentStatement statement && element.isWritable()) {

            PyExpression target = statement.getLeftHandSideExpression();
            PyBinaryExpression expression = (PyBinaryExpression)statement.getAssignedValue();
            if (expression == null) {
                return;
            }
            PyExpression leftExpression = expression.getLeftExpression();
            PyExpression rightExpression = expression.getRightExpression();
            if (rightExpression instanceof PyParenthesizedExpression) {
                rightExpression = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
            }
            if (target != null && rightExpression != null) {
                String targetText = target.getText();
                String rightText = rightExpression.getText();
                if (rightText.equals(targetText)) {
                    PyExpression tmp = rightExpression;
                    rightExpression = leftExpression;
                    leftExpression = tmp;
                }
                List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(statement, PsiComment.class);

                if ((leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
                    if (leftExpression.getText().equals(targetText)) {
                        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
                        StringBuilder stringBuilder = new StringBuilder();
                        PsiElement psiOperator = expression.getPsiOperator();
                        if (psiOperator == null) {
                            return;
                        }
                        stringBuilder.append(targetText).append(" ").
                            append(psiOperator.getText()).append("= ").append(rightExpression.getText());
                        PyAugAssignmentStatementImpl augAssignment = elementGenerator.createFromText(LanguageLevel.forElement(element),
                                PyAugAssignmentStatementImpl.class,
                                stringBuilder.toString());
                        for (PsiComment comment : comments) {
                            augAssignment.add(comment);
                        }
                        statement.replace(augAssignment);
                    }
                }
            }
        }
    }
}
