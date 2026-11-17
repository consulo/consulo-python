// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements;

import com.intellij.python.requirements.parser.RequirementsLexerAdapter;
import com.intellij.python.requirements.parser.psi.RequirementsTypes;
import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.codeEditor.HighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.lexer.Lexer;

import static consulo.colorScheme.TextAttributesKey.createTextAttributesKey;

public class RequirementsSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey BAD_CHARACTER = createTextAttributesKey(
        "REQUIREMENTS_BAD_CHARACTER",
        HighlighterColors.BAD_CHARACTER
    );
    public static final TextAttributesKey COMMA = createTextAttributesKey(
        "REQUIREMENTS_COMMA",
        DefaultLanguageHighlighterColors.COMMA
    );
    public static final TextAttributesKey COMMENT = createTextAttributesKey(
        "REQUIREMENTS_COMMENT",
        DefaultLanguageHighlighterColors.LINE_COMMENT
    );
    public static final TextAttributesKey ENV_MARKER_NAME = createTextAttributesKey(
        "REQUIREMENTS_ENV_MARKER_NAME",
        DefaultLanguageHighlighterColors.IDENTIFIER
    );
    public static final TextAttributesKey PACKAGE_NAME = createTextAttributesKey(
        "REQUIREMENTS_PACKAGE_NAME",
        DefaultLanguageHighlighterColors.KEYWORD
    );
    public static final TextAttributesKey QUOTED_STRING = createTextAttributesKey(
        "REQUIREMENTS_QUOTED_STRING",
        DefaultLanguageHighlighterColors.STRING
    );
    public static final TextAttributesKey VERSION = createTextAttributesKey(
        "REQUIREMENTS_VERSION",
        DefaultLanguageHighlighterColors.STRING
    );
    public static final TextAttributesKey OPERATOR = createTextAttributesKey(
        "REQUIREMENTS_OPERATOR",
        DefaultLanguageHighlighterColors.KEYWORD
    );
    public static final TextAttributesKey SEMICOLON = createTextAttributesKey(
        "REQUIREMENTS_SEMICOLON",
        DefaultLanguageHighlighterColors.SEMICOLON
    );

    private static final TextAttributesKey[] BAD_CHAR_KEYS = {BAD_CHARACTER};
    private static final TextAttributesKey[] BRACE_KEYS = {COMMENT};
    private static final TextAttributesKey[] COMMENT_KEYS = {COMMENT};
    private static final TextAttributesKey[] COMMA_KEYS = {COMMA};
    private static final TextAttributesKey[] EMPTY_KEYS = TextAttributesKey.EMPTY_ARRAY;
    private static final TextAttributesKey[] ENV_MARKER_NAME_KEYS = {ENV_MARKER_NAME};
    private static final TextAttributesKey[] PACKAGE_NAME_KEYS = {PACKAGE_NAME};
    private static final TextAttributesKey[] OPERATOR_KEYS = {OPERATOR};
    private static final TextAttributesKey[] QUOTED_STRING_KEYS = {QUOTED_STRING};
    private static final TextAttributesKey[] SEMICOLON_KEYS = {SEMICOLON};
    private static final TextAttributesKey[] VERSION_KEYS = {VERSION};

    @Override
    public Lexer getHighlightingLexer() {
        return new RequirementsLexerAdapter();
    }

    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType == TokenType.BAD_CHARACTER) {
            return BAD_CHAR_KEYS;
        }
        else if (tokenType == RequirementsTypes.AND) {
            return OPERATOR_KEYS;
        }
        else if (tokenType == RequirementsTypes.COMMA) {
            return COMMA_KEYS;
        }
        else if (tokenType == RequirementsTypes.COMMENT) {
            return COMMENT_KEYS;
        }
        else if (tokenType == RequirementsTypes.ENV_MARKER_NAME) {
            return ENV_MARKER_NAME_KEYS;
        }
        else if (tokenType == RequirementsTypes.LSBRACE) {
            return BRACE_KEYS;
        }
        else if (tokenType == RequirementsTypes.IN_OP) {
            return OPERATOR_KEYS;
        }
        else if (tokenType == RequirementsTypes.NOTIN_OP) {
            return OPERATOR_KEYS;
        }
        else if (tokenType == RequirementsTypes.OR) {
            return OPERATOR_KEYS;
        }
        else if (tokenType == RequirementsTypes.PACKAGE_NAME_TOKEN) {
            return PACKAGE_NAME_KEYS;
        }
        else if (tokenType == RequirementsTypes.QUOTED_STRING_TOKEN) {
            return QUOTED_STRING_KEYS;
        }
        else if (tokenType == RequirementsTypes.RSBRACE) {
            return BRACE_KEYS;
        }
        else if (tokenType == RequirementsTypes.SEMICOLON) {
            return SEMICOLON_KEYS;
        }
        else if (tokenType == RequirementsTypes.VERSION_TOKEN) {
            return VERSION_KEYS;
        }
        else {
            return EMPTY_KEYS;
        }
    }
}
