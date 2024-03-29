/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.editor.selectWord;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.lexer.PyStringLiteralLexer;
import com.jetbrains.python.psi.PyStringLiteralExpression;
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

/**
 * @author yole
 */
@ExtensionImpl
public class PyLiteralSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(PsiElement e) {
    final ASTNode node = e.getNode();
    return node != null && PyTokenTypes.STRING_NODES.contains(node.getElementType());
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    final PyStringLiteralExpression literal = PsiTreeUtil.getParentOfType(e, PyStringLiteralExpression.class);
    if (literal != null) {
      List<TextRange> ranges = literal.getStringValueTextRanges();
      List<ASTNode> nodes = literal.getStringNodes();
      for (int i = 0; i < ranges.size(); i++) {
        TextRange stringRange = ranges.get(i);
        TextRange offsetRange = stringRange.shiftRight(literal.getTextRange().getStartOffset());
        if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
          List<TextRange> result = new ArrayList<TextRange>();
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
