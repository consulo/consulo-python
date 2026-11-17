/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.editorActions;

import com.jetbrains.python.impl.editor.BaseQuoteHandler;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.HighlighterIterator;
import consulo.ide.impl.idea.codeInsight.editorActions.TypedHandler;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.BackspaceHandlerDelegate;
import consulo.language.editor.action.QuoteHandler;
import consulo.language.psi.PsiFile;

@ExtensionImpl
public final class PyTripleQuoteBackspaceDelegate extends BackspaceHandlerDelegate {
    private boolean isTripleQuote;

    @Override
    public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
        isTripleQuote = false;
        if (c == '"' || c == '\'' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
            QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(file, editor);
            if (!(quoteHandler instanceof BaseQuoteHandler)) {
                return;
            }

            int offset = editor.getCaretModel().getCurrentCaret().getOffset();
            CharSequence text = editor.getDocument().getCharsSequence();
            boolean mayBeTripleQuote = offset >= 3 && offset + 2 < text.length();
            if (mayBeTripleQuote) {
                int quoteOffset = offset - 1;
                HighlighterIterator iterator = editor.getHighlighter().createIterator(quoteOffset);
                boolean hasTripleQuoteAfter = offset + 2 < text.length() &&
                    text.charAt(offset) == c && text.charAt(offset + 1) == c && text.charAt(offset + 2) == c;
                isTripleQuote = quoteHandler.isOpeningQuote(iterator, quoteOffset) && hasTripleQuoteAfter;
            }
        }
    }

    @Override
    public boolean charDeleted(char c, PsiFile file, Editor editor) {
        if (isTripleQuote) {
            int offset = editor.getCaretModel().getCurrentCaret().getOffset();
            editor.getDocument().deleteString(offset - 2, offset + 3);
            return true;
        }
        return false;
    }
}
