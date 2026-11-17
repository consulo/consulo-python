// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

public class PyClassFixer extends PyFixer<PyClass> {
    public PyClassFixer() {
        super(PyClass.class);
    }

    @Override
    public void doApply(Editor editor, PySmartEnterProcessor processor, PyClass pyClass) throws IncorrectOperationException {
        PsiElement colon = PyPsiUtils.getFirstChildOfType(pyClass, PyTokenTypes.COLON);
        if (colon == null) {
            PyArgumentList argList = PsiTreeUtil.getChildOfType(pyClass, PyArgumentList.class);
            int colonOffset = sure(argList).getTextRange().getEndOffset();
            String textToInsert = ":";
            if (pyClass.getNameNode() == null) {
                int newCaretOffset = argList.getTextOffset();
                if (argList.getTextLength() == 0) {
                    newCaretOffset += 1;
                    textToInsert = " :";
                }
                processor.registerUnresolvedError(newCaretOffset);
            }
            editor.getDocument().insertString(colonOffset, textToInsert);
        }
    }
}
