// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyRemoveAssignmentStatementTargetQuickFix implements LocalQuickFix, HighPriorityAction {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveAssignmentTarget();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
        if (assignmentStatement == null) {
            return;
        }
        if (assignmentStatement.getRawTargets().length == 1) {
            final PyExpression expression = assignmentStatement.getAssignedValue();
            if (expression == null) {
                return;
            }
            final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            PyExpressionStatement statement = elementGenerator.createFromText(
                LanguageLevel.forElement(expression),
                PyExpressionStatement.class,
                expression.getText()
            );
            assignmentStatement.replace(statement);
        }
        else {
            PsiElement possibleNextEq = PsiTreeUtil.nextVisibleLeaf(element);
            if (possibleNextEq == null) {
                return;
            }
            assert possibleNextEq.getNode().getElementType() == PyTokenTypes.EQ;
            element.getParent().deleteChildRange(element, possibleNextEq);
        }
    }
}
