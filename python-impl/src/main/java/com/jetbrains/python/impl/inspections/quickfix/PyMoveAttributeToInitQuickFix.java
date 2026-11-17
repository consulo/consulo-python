// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyMoveAttributeToInitQuickFix implements LocalQuickFix {

    public PyMoveAttributeToInitQuickFix() {
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixMoveAttribute();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PyTargetExpression targetExpression)) {
            return;
        }

        PyClass containingClass = targetExpression.getContainingClass();
        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
        if (containingClass == null || assignment == null) {
            return;
        }

        AddFieldQuickFix.addFieldToInit(project, containingClass, targetExpression.getName(), x -> assignment);
        removeDefinition(assignment);
    }

    private static void removeDefinition(PyAssignmentStatement assignment) {
        PyStatementList statementList = PsiTreeUtil.getParentOfType(assignment, PyStatementList.class);
        if (statementList == null) {
            return;
        }
        assignment.delete();
    }
}
