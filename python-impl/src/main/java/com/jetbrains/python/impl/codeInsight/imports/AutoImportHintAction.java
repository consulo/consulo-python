// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.codeEditor.Editor;
import consulo.language.editor.AutoImportHelper;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.HintAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class AutoImportHintAction implements LocalQuickFix, HintAction, HighPriorityAction {
    private final AutoImportQuickFix myDelegate;

    public AutoImportHintAction(AutoImportQuickFix delegate) {
        myDelegate = delegate;
    }

    @Override
    public boolean showHint(Editor editor) {
        List<? extends ImportCandidateHolder> imports = myDelegate.getCandidates();
        if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP ||
            HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true) ||
            imports.isEmpty()) {
            return false;
        }
        final PsiElement element = myDelegate.getStartElement();
        PyPsiUtils.assertValid(element);
        if (element == null || !element.isValid()) {
            return false;
        }
        final PyElement pyElement = as(element, PyElement.class);
        String initialName = myDelegate.getNameToImport();
        if (pyElement == null || !initialName.equals(pyElement.getName())) {
            return false;
        }
        final PsiReference reference = myDelegate.findOriginalReference(element);
        if (reference == null || AutoImportQuickFix.isResolved(reference)) {
            return false;
        }
        if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).isQualified()) {
            return false; // we cannot be qualified
        }

        // Consulo: JB ShowAutoImportPass.getMessage(multiple, DaemonBundle.message("symbol"), name) is AutoImportHelper#getImportMessage
        final String message = AutoImportHelper.getInstance(element.getProject()).getImportMessage(
            imports.size() > 1,
            ImportCandidateHolder.getQualifiedName(initialName, imports.get(0).getPath(), imports.get(0).getImportElement())
        );
        final ImportFromExistingAction action = myDelegate.createAction(element);
        HintManager.getInstance().showQuestionHint(
            editor, message,
            element.getTextOffset(),
            element.getTextRange().getEndOffset(), action);
        return true;
    }

    @Override
    public LocalizeValue getText() {
        return myDelegate.getText();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        return myDelegate.isAvailable();
    }

    @Override
    public void invoke(Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        myDelegate.invoke();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public LocalizeValue getName() {
        return myDelegate.getName();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        myDelegate.applyFix(project, descriptor);
    }

    public AutoImportQuickFix getDelegate() {
        return myDelegate;
    }
}
