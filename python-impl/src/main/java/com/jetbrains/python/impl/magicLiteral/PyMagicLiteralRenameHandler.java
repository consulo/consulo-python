// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.magicLiteral;

import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameDialog;
import consulo.language.editor.refactoring.rename.RenameHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyMagicLiteralRenameHandler implements RenameHandler {
    /**
     * @return string literal under data context or null if not a literal.
     * This method is fast, so it can safely be used at {@link #isAvailableOnDataContext(DataContext)}
     */
    @RequiredReadAction
    private static @Nullable PyStringLiteralExpression getStringLiteral(DataContext dataContext) {
        Editor editor = dataContext.getData(Editor.KEY);
        if (editor == null) {
            return null;
        }

        PsiFile file = dataContext.getData(PsiFile.KEY);
        if (file == null) {
            return null;
        }

        PsiElement element = getElement(file, editor);
        if (element instanceof PyStringLiteralExpression) {
            return (PyStringLiteralExpression) element;
        }
        return null;
    }

    @RequiredReadAction
    private static @Nullable PsiElement getElement(PsiFile file, Editor editor) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getCurrentCaret().getOffset());
        StringLiteralExpression stringLiteral = PsiTreeUtil.getParentOfType(element, StringLiteralExpression.class, false,
            PyFStringFragment.class);
        if (stringLiteral instanceof PyStringLiteralExpression && ((PyStringLiteralExpression) stringLiteral).isInterpolated()) {
            return null;
        }
        return stringLiteral;
    }

    @Override
    @RequiredReadAction
    public boolean isAvailableOnDataContext(DataContext dataContext) {
        return getStringLiteral(dataContext) != null;
    }

    @Override
    @RequiredReadAction
    public boolean isRenaming(DataContext dataContext) {
        PyStringLiteralExpression literal = getStringLiteral(dataContext);
        return !((literal == null) || !PyMagicLiteralTools.couldBeMagicLiteral(literal));
    }

    @Override
    public LocalizeValue getActionTitleValue() {
        return RefactoringLocalize.renameTitle();
    }

    @Override
    @RequiredUIAccess
    public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
        PsiElement element = getElement(file, editor);
        if (element == null) {
            return;
        }
        RenameDialog.showRenameDialog(dataContext, new RenameDialog(project, element, null, editor));
    }

    @Override
    public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
        throw new UnsupportedOperationException();
    }
}
