/*
  * Copyright 2000-2014 JetBrains s.r.o.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;

/**
  * @author Mikhail Golubev
  */
public class PyWithFixer extends PyFixer<PyWithStatement> {
    public PyWithFixer() {
        super(PyWithStatement.class);
    }

    @Override
    public void doApply(Editor editor, PySmartEnterProcessor processor, PyWithStatement withStatement) throws IncorrectOperationException {
        PsiElement withToken = PyPsiUtils.getFirstChildOfType(withStatement, PyTokenTypes.WITH_KEYWORD);
        assert withToken != null;
        Document document = editor.getDocument();
        PsiElement colonToken = PyPsiUtils.getFirstChildOfType(withStatement, PyTokenTypes.COLON);
        if (colonToken == null) {
            PsiElement closingParenthesis = PyPsiUtils.getFirstChildOfType(withStatement, PyTokenTypes.RPAR);
            PyWithItem lastWithItem = ArrayUtil.getLastElement(withStatement.getWithItems());
            PsiElement rightmostElement = closingParenthesis != null ? closingParenthesis :
                                          lastWithItem != null ? lastWithItem :
                                          withToken;
            document.insertString(rightmostElement.getTextRange().getEndOffset(), ":");
        }

        if (withStatement.getWithItems().length != 0) {
            for (PyWithItem withItem : withStatement.getWithItems()) {
                PsiElement asToken = PyPsiUtils.getFirstChildOfType(withItem, PyTokenTypes.AS_KEYWORD);
                if (asToken != null && withItem.getTarget() == null) {
                    int asKeywordEndOffset = asToken.getTextRange().getEndOffset();
                    if (!(PsiTreeUtil.nextLeaf(asToken, true) instanceof PsiWhiteSpace)) {
                        document.insertString(asKeywordEndOffset," ");
                    }
                    processor.registerUnresolvedError(asKeywordEndOffset + 1);
                    break;
                }
            }
        }
        else {
            int withKeywordEndOffset = withToken.getTextRange().getEndOffset();
            if (!(PsiTreeUtil.nextLeaf(withToken, true) instanceof PsiWhiteSpace)) {
                document.insertString(withKeywordEndOffset, " ");
                processor.registerUnresolvedError(withKeywordEndOffset + 1);
            }
        }
    }
}
