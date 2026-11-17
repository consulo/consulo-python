// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

/**
 * User: catherine
 * <p>
 * QuickFix to replace true with True, false with False
 */
public class UnresolvedRefTrueFalseQuickFix implements LocalQuickFix {
    String newName;

    public UnresolvedRefTrueFalseQuickFix(String oldName) {
        newName = StringUtil.capitalize(oldName);
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixReplaceWithTrueOrFalse(newName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), newName);
        element.replace(expression);
    }
}
