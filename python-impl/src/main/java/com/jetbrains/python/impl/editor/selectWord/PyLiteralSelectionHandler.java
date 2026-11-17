// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.editor.selectWord;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.lexer.PyStringLiteralLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.action.ExtendWordSelectionHandlerBase;
import consulo.language.editor.action.SelectWordUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public final class PyLiteralSelectionHandler extends ExtendWordSelectionHandlerBase {
    @Override
    public boolean canSelect(PsiElement e) {
        ASTNode node = e.getNode();
        return node != null && PyTokenTypes.STRING_NODES.contains(node.getElementType());
    }

    @Override
    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
        PyAstStringLiteralExpression literal = PsiTreeUtil.getParentOfType(e, PyAstStringLiteralExpression.class);
        if (literal != null) {
            List<TextRange> ranges = literal.getStringValueTextRanges();
            List<ASTNode> nodes = literal.getStringNodes();
            for (int i = 0; i < ranges.size(); i++) {
                TextRange stringRange = ranges.get(i);
                TextRange offsetRange = stringRange.shiftRight(literal.getTextRange().getStartOffset());
                if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
                    List<TextRange> result = new ArrayList<>();
                    SelectWordUtil.addWordHonoringEscapeSequences(editorText, nodes.get(i).getTextRange(), cursorOffset,
                                                                    new PyStringLiteralLexer(nodes.get(i).getElementType()),
                                                                    result);
                    result.add(offsetRange);
                    return result;
                }
            }
        }
        return Collections.emptyList();
    }
}
