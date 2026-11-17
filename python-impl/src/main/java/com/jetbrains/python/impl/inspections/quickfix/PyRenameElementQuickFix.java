// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.codeEditor.Editor;
import consulo.content.scope.SearchScope;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.editor.refactoring.rename.inplace.VariableInplaceRenamer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * @see consulo.language.editor.util.PsiEditorUtil#findEditor
 */
public class PyRenameElementQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement implements LocalQuickFix {

    public PyRenameElementQuickFix(@Nullable PsiElement element) {
        super(element);
    }

    // JB getFamilyName(): PyCodeInsightBundle.message("QFIX.FAMILY.NAME.rename.element") - Consulo quick fixes have no family name

    @Override
    public LocalizeValue getText() {
        return PyLocalize.qfixNameRenameElement();
    }

    @Override
    public void invoke(Project project,
                       PsiFile psiFile,
                       @Nullable Editor editor,
                       PsiElement startElement,
                       PsiElement endElement) {
        PsiElement element = PyQuickFixUtil.dereference(startElement);
        if (element == null) {
            return;
        }
        PsiNameIdentifierOwner nameOwner = element instanceof PsiNameIdentifierOwner ?
            (PsiNameIdentifierOwner) element :
            PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner.class, true);
        if (nameOwner == null || editor == null) {
            return;
        }
        if (checkLocalScope(element) != null && (nameOwner instanceof PyNamedParameter || nameOwner instanceof PyTargetExpression)) {
            new VariableInplaceRenamer(nameOwner, editor).performInplaceRename();
        }
        else {
            PsiElementRenameHandler.invoke(nameOwner, project, ScopeUtil.getScopeOwner(nameOwner), editor);
        }
    }

    protected @Nullable PsiElement checkLocalScope(PsiElement element) {
        SearchScope searchScope = PsiSearchHelper.getInstance(element.getProject()).getUseScope(element);
        if (searchScope instanceof LocalSearchScope) {
            PsiElement[] elements = ((LocalSearchScope) searchScope).getScope();
            return PsiTreeUtil.findCommonParent(elements);
        }

        return null;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    // TODO consulo: LocalQuickFix#availableInBatchMode() missing - JB returns false

    @Override
    public @Nullable PsiElement getElementToMakeWritable(PsiFile file) {
        return file;
    }
}
