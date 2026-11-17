// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyImportStatementBase;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * Quick fix that adds import to file
 *
 * @author Ilya.Kazakevich
 */
// Consulo: JB extends PsiUpdateModCommandQuickFix (ModCommand API missing), ported as a classic LocalQuickFix
public class PyAddImportFix implements LocalQuickFix {
    private final String myImportToAdd;

    /**
     * @param importToAdd string representing what to add (i.e. "from foo import bar")
     */
    public PyAddImportFix(String importToAdd) {
        myImportToAdd = importToAdd;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddImportAddImport(myImportToAdd);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PsiFile file = element.getContainingFile();
        PyImportStatementBase statement =
            generator.createFromText(LanguageLevel.forElement(file), PyImportStatementBase.class, myImportToAdd);
        PsiElement recommendedPosition = AddImportHelper.getFileInsertPosition(file);
        file.addAfter(statement, recommendedPosition);
    }
}
