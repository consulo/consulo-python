// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyRemoveCallQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveCall();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        assert element instanceof PyCallExpression;
        if (element instanceof PyDecorator) {
            element.delete();
        }
        else {
            final PyArgumentList argumentList = ((PyCallExpression)element).getArgumentList();
            assert argumentList != null;
            argumentList.delete();
            final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            // regenerate element for Psi consistency, because it isn't PyCallExpression anymore
            final PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), element.getText());
            element.replace(expression);
        }
    }
}
