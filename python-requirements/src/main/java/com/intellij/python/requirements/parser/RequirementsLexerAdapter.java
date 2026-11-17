// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements.parser;

import consulo.language.lexer.FlexAdapter;
import org.jspecify.annotations.NullUnmarked;

public class RequirementsLexerAdapter extends FlexAdapter {
    @NullUnmarked
    public RequirementsLexerAdapter() {
        super(new RequirementsLexer(null));
    }

    /**
     * {@link FlexAdapter} reuses one {@link RequirementsLexer} instance and calls its generated {@code reset()} on every
     * (re)start. That reset restores the JFlex {@code zz*} fields but not the custom {@code yypush}/{@code yypop} state
     * stack, so incremental re-lexing in the editor would carry a stale stack from the previous run and
     * mis-tokenize — e.g. a version losing its highlight when a space is typed before {@code ;}. Injected
     * fragments ({@code pyproject.toml}) are unaffected because they are always lexed from scratch.
     * <p>
     * Rebuild the stack deterministically from {@code initialState} before each start so re-lexing is a pure
     * function of {@code (text, initialState)}, matching the stack a full scan would have at that state.
     */
    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
        RequirementsLexer lexer = (RequirementsLexer) getFlex();
        lexer.stack.clear();
        if (initialState != RequirementsLexer.YYINITIAL) {
            lexer.stack.push(RequirementsLexer.YYINITIAL);
        }
        lexer.stack.push(initialState);
        super.start(buffer, startOffset, endOffset, initialState);
    }
}
