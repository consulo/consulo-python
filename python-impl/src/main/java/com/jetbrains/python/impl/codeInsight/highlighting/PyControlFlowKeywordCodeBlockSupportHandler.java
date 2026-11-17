// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.highlighting;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.highlight.CodeBlockSupportHandler;
import consulo.language.psi.PsiElement;

import java.util.List;

/**
 * Plugs the related keywords of a Python multipart compound statement (`if`/`elif`/`else`,
 * `for`/`else`, `while`/`else`, `try`/`except`/`else`/`finally`, `match`/`case`) into the platform
 * code-block machinery: they are highlighted together when the caret is on one of them and the
 * standard "Move Caret to Matching Brace" action jumps between the statement boundaries.
 * <p>
 * The off-screen preview of the header is provided separately by PyControlFlowKeywordBraceHighlighter.
 */
@ExtensionImpl
public class PyControlFlowKeywordCodeBlockSupportHandler implements CodeBlockSupportHandler {
    @Override
    public List<TextRange> getCodeBlockMarkerRanges(PsiElement elementAtCursor) {
        List<TextRange> ranges = PyControlFlowKeywordMatcher.compoundStatementKeywordRanges(elementAtCursor);
        return ranges != null ? ranges : List.of();
    }

    @Override
    public TextRange getCodeBlockRange(PsiElement elementAtCursor) {
        TextRange range = PyControlFlowKeywordMatcher.compoundStatementRange(elementAtCursor);
        return range != null ? range : TextRange.EMPTY_RANGE;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
