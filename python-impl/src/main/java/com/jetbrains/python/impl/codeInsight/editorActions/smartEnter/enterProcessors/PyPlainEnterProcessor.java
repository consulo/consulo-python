// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.enterProcessors;

import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.SmartEnterUtil;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStatementPart;
import org.jspecify.annotations.Nullable;

public class PyPlainEnterProcessor implements EnterProcessor {
    private static @Nullable PyStatementList getStatementList(PsiElement psiElement, Editor editor) {
        if (psiElement instanceof PyStatementListContainer) {
            return ((PyStatementListContainer)psiElement).getStatementList();
        }
        else {
            CaretModel caretModel = editor.getCaretModel();
            PsiElement atCaret = psiElement.getContainingFile().findElementAt(caretModel.getOffset());
            PyStatementPart statementPart = PsiTreeUtil.getParentOfType(atCaret, PyStatementPart.class);
            if (statementPart != null) {
                return statementPart.getStatementList();
            }
        }
        return null;
    }

    @Override
    public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
        PyStatementList statementList = getStatementList(psiElement, editor);
        if (statementList != null && statementList.getStatements().length == 0) {
            SmartEnterUtil.plainEnter(editor);
            //editor.getCaretModel().moveToOffset(statementList.getTextRange().getEndOffset());
            return true;
        }
        return false;
    }
}
