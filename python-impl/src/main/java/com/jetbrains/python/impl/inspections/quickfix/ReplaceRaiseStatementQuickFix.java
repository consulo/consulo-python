// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class ReplaceRaiseStatementQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.intnReplaceRaiseStatement();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyRaiseStatement) {
            PyExpression[] expressions = ((PyRaiseStatement)element).getExpressions();
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            String newExpressionText = expressions[0].getText() + "(" + expressions[1].getText() + ")";
            if (expressions.length == 2) {
                element.replace(
                    elementGenerator.createFromText(LanguageLevel.forElement(element), PyRaiseStatement.class, "raise " + newExpressionText));
            }
            else if (expressions.length == 3) {
                element.replace(elementGenerator.createFromText(LanguageLevel.forElement(element), PyRaiseStatement.class,
                        "raise " +
                        newExpressionText +
                        ".with_traceback(" +
                        expressions[2].getText() +
                        ")"));
            }
        }
    }
}
