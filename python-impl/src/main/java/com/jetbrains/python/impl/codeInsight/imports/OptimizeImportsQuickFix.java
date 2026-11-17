// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.psi.PyFile;
import consulo.codeEditor.Editor;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.refactoring.ImportOptimizer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;


public class OptimizeImportsQuickFix implements LocalQuickFix, IntentionAction, HighPriorityAction {

    @Override
    public LocalizeValue getText() {
        return getName();
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixOptimizeImports();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        return psiFile instanceof PyFile;
    }

    @Override
    @RequiredUIAccess
    public void invoke(Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        optimizeImports(project, psiFile);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    @RequiredUIAccess
    public void applyFix(final Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {  // stale PSI
            return;
        }
        final PsiFile file = element.getContainingFile();
        optimizeImports(project, file);
    }

    @RequiredUIAccess
    private void optimizeImports(final Project project, final PsiFile file) {
        ImportOptimizer optimizer = new PyImportOptimizer();
        final Runnable runnable = optimizer.processFile(file);
        WriteCommandAction.writeCommandAction(file).withName(getName().get()).run(() -> runnable.run());
    }
}
