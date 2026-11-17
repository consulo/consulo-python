// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.ast.TokenSet;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

public class PyUnconditionalStatementPartFixer extends PyFixer<PyElement> {
    public PyUnconditionalStatementPartFixer() {
        super(PyElement.class);
    }

    @Override
    public void doApply(Editor editor, PySmartEnterProcessor processor, PyElement psiElement)
        throws IncorrectOperationException {
        if (PsiTreeUtil.instanceOf(psiElement, PyElsePart.class, PyTryPart.class, PyFinallyPart.class)) {
            PsiElement colon = PyPsiUtils.getFirstChildOfType(psiElement, PyTokenTypes.COLON);
            if (colon == null) {
                TokenSet keywords = TokenSet.create(PyTokenTypes.ELSE_KEYWORD, PyTokenTypes.TRY_KEYWORD, PyTokenTypes.FINALLY_KEYWORD);
                PsiElement keywordToken = PyPsiUtils.getChildByFilter(psiElement, keywords, 0);
                editor.getDocument().insertString(sure(keywordToken).getTextRange().getEndOffset(), ":");
            }
        }
    }
}
