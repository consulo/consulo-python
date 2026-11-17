// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.enterProcessors;

import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.SmartEnterUtil;

public class PyCommentBreakerEnterProcessor implements EnterProcessor {
    @Override
    public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
        if (isModified) {
            return false;
        }
        CaretModel caretModel = editor.getCaretModel();
        PsiElement atCaret = psiElement.getContainingFile().findElementAt(caretModel.getOffset());
        if (atCaret instanceof PsiWhiteSpace) {
            atCaret = atCaret.getPrevSibling();
        }
        PsiElement comment = PsiTreeUtil.getParentOfType(atCaret, PsiComment.class, false);
        if (comment != null) {
            SmartEnterUtil.plainEnter(editor);
            editor.getDocument().insertString(caretModel.getOffset(), "# ");
            caretModel.moveToOffset(caretModel.getOffset() + 2);
            return true;
        }
        return false;
    }
}
