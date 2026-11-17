// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.editor.selectWord;

import com.jetbrains.python.ast.PyAstCallExpression;
import com.jetbrains.python.ast.PyAstStatement;
import com.jetbrains.python.ast.PyAstStatementList;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.action.ExtendWordSelectionHandlerBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class PyStatementSelectionHandler extends ExtendWordSelectionHandlerBase {
    @Override
    public boolean canSelect(PsiElement e) {
        return e instanceof PyAstStringLiteralExpression || e instanceof PyAstCallExpression || e instanceof PyAstStatement ||
                      e instanceof PyAstStatementList;
    }

    @Override
    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
        PsiElement endElement = e;
        while(endElement.getLastChild() != null) {
            endElement = endElement.getLastChild();
        }
        if (endElement instanceof PsiWhiteSpace) {
            PsiElement prevSibling = endElement.getPrevSibling();
            if (prevSibling != null) {
                endElement = prevSibling;
            }
        }

        return new ArrayList<>(expandToWholeLine(editorText, new TextRange(e.getTextRange().getStartOffset(),
                                                                                   endElement.getTextRange().getEndOffset())));
    }
}
