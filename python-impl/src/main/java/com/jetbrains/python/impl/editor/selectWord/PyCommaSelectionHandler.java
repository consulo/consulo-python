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
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.ExtendWordSelectionHandlerBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;

import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 * <p>
 * Handler to select commas around a selection before widening the selection to few words
 */
@ExtensionImpl
public class PyCommaSelectionHandler extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(final PsiElement e) {
    return e instanceof PyReferenceExpression || e instanceof PyKeyValueExpression || e instanceof PyKeywordArgument
      || e instanceof PyNumericLiteralExpression || e instanceof PyStringLiteralExpression || e instanceof PyNamedParameter
      || e instanceof PyStarArgument;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    if (e != null) {
      List<TextRange> textRange = addNextComma(e, cursorOffset);
      if (textRange.equals(Collections.emptyList())) return addPreviousComma(e, cursorOffset);
      else return textRange;
    }
    return Collections.emptyList();
  }

  /**
   * adds previous comma and whitespace to result text range
   *
   * @param e            is current element
   * @param cursorOffset is current cursor offset
   * @return result selection textRange
   */
  private static List<TextRange> addPreviousComma(PsiElement e, int cursorOffset) {
    PsiElement prevSibling = e.getPrevSibling();
    TextRange textRange = e.getTextRange();
    TextRange offsetRange = null;
    if (prevSibling != null) {
      if (prevSibling instanceof PsiWhiteSpace) {
        PsiElement prevCommaSibling = prevSibling.getPrevSibling();
        if (prevCommaSibling != null) {
          ASTNode node = prevCommaSibling.getNode();
          if (node != null) {
            IElementType commaType = node.getElementType();
            if (commaType == PyTokenTypes.COMMA) {
              offsetRange = new TextRange(textRange.getStartOffset() - 2, textRange.getEndOffset());
              if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
                return Collections.singletonList(offsetRange);
              }
            }
          }
        }
      }
      else {
        ASTNode node = prevSibling.getNode();
        if (node != null) {
          IElementType commaType = node.getElementType();
          if (commaType == PyTokenTypes.COMMA) {
            offsetRange = new TextRange(textRange.getStartOffset() - 1, textRange.getEndOffset());
          }
        }
      }
      if (offsetRange != null) {
        if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
          return Collections.singletonList(offsetRange);
        }
      }
    }
    return Collections.emptyList();
  }

  /**
   * add next comma and whitespace to selection
   *
   * @param e            is crrent element
   * @param cursorOffset is current cursor offset
   * @return result selection TextRange
   */
  private static List<TextRange> addNextComma(PsiElement e, int cursorOffset) {
    PsiElement nextCommaSibling = e.getNextSibling();
    if (nextCommaSibling != null) {
      ASTNode node = nextCommaSibling.getNode();
      if (node != null) {
        IElementType commaType = node.getElementType();
        if (commaType == PyTokenTypes.COMMA) {
          PsiElement nextSpaceSibling = nextCommaSibling.getNextSibling();
          if (nextSpaceSibling != null) {
            TextRange textRange = e.getTextRange();
            TextRange offsetRange;
            if (nextSpaceSibling instanceof PsiWhiteSpace) {
              offsetRange = new TextRange(textRange.getStartOffset(), textRange.getEndOffset() + 2);
            }
            else {
              offsetRange = new TextRange(textRange.getStartOffset(), textRange.getEndOffset() + 1);
            }
            if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
              return Collections.singletonList(offsetRange);
            }
          }
        }
      }
    }
    return Collections.emptyList();
  }
}
