// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.highlighting;

import consulo.language.lexer.LayeredLexer;
import consulo.language.lexer.Lexer;
import consulo.codeEditor.HighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.ast.StringEscapesTokenTypes;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.lexer.PyFStringLiteralLexer;
import com.jetbrains.python.lexer.PyStringLiteralLexer;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.HashMap;
import java.util.Map;

import static consulo.codeEditor.DefaultLanguageHighlighterColors.BRACES;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.BRACKETS;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.CLASS_NAME;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.COMMA;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.DOC_COMMENT;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.DOC_COMMENT_TAG;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.DOT;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.FUNCTION_CALL;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.FUNCTION_DECLARATION;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.IDENTIFIER;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.KEYWORD;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.LINE_COMMENT;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.LOCAL_VARIABLE;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.METADATA;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.NUMBER;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.OPERATION_SIGN;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.PARAMETER;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.PARENTHESES;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.STRING;
import static consulo.codeEditor.DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE;

/**
 * Colors and lexer(s) needed for highlighting.
 */
public class PyHighlighter extends SyntaxHighlighterBase {
  private final Map<IElementType, TextAttributesKey> keys;
  private final LanguageLevel myLanguageLevel;

  @Override
  public Lexer getHighlightingLexer() {
    LayeredLexer ret = new LayeredLexer(createHighlightingLexer(myLanguageLevel));
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_STRING),
      PyTokenTypes.SINGLE_QUOTED_STRING
    );
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
      PyTokenTypes.SINGLE_QUOTED_UNICODE
    );
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.TRIPLE_QUOTED_STRING),
      PyTokenTypes.TRIPLE_QUOTED_STRING
    );
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.TRIPLE_QUOTED_UNICODE),
      PyTokenTypes.TRIPLE_QUOTED_UNICODE
    );
    ret.registerLayer(
      new PyFStringLiteralLexer(PyTokenTypes.FSTRING_TEXT),
      PyTokenTypes.FSTRING_TEXT
    );
    ret.registerLayer(
      new PyFStringLiteralLexer(PyTokenTypes.FSTRING_RAW_TEXT),
      PyTokenTypes.FSTRING_RAW_TEXT
    );

    return ret;
  }

  protected PythonHighlightingLexer createHighlightingLexer(LanguageLevel languageLevel) {
    return new PythonHighlightingLexer(myLanguageLevel);
  }

  public static final TextAttributesKey PY_KEYWORD = TextAttributesKey.of("PY.KEYWORD", KEYWORD);

  public static final TextAttributesKey PY_BYTE_STRING = TextAttributesKey.of("PY.STRING.B", STRING);
  public static final TextAttributesKey PY_UNICODE_STRING = TextAttributesKey.of("PY.STRING.U", STRING);
  public static final TextAttributesKey PY_NUMBER = TextAttributesKey.of("PY.NUMBER", NUMBER);

  public static final TextAttributesKey PY_OPERATION_SIGN = TextAttributesKey.of("PY.OPERATION_SIGN", OPERATION_SIGN);

  public static final TextAttributesKey PY_PARENTHS = TextAttributesKey.of("PY.PARENTHS", PARENTHESES);

  public static final TextAttributesKey PY_BRACKETS = TextAttributesKey.of("PY.BRACKETS", BRACKETS);

  public static final TextAttributesKey PY_BRACES = TextAttributesKey.of("PY.BRACES", BRACES);

  public static final TextAttributesKey PY_COMMA = TextAttributesKey.of("PY.COMMA", COMMA);

  public static final TextAttributesKey PY_DOT = TextAttributesKey.of("PY.DOT", DOT);

  public static final TextAttributesKey PY_LINE_COMMENT = TextAttributesKey.of("PY.LINE_COMMENT", LINE_COMMENT);

  public static final TextAttributesKey PY_DOC_COMMENT = TextAttributesKey.of("PY.DOC_COMMENT", DOC_COMMENT);

  public static final TextAttributesKey PY_DOC_COMMENT_TAG = TextAttributesKey.of("PY.DOC_COMMENT_TAG", DOC_COMMENT_TAG);

  public static final TextAttributesKey PY_DECORATOR = TextAttributesKey.of("PY.DECORATOR", METADATA);

  public static final TextAttributesKey PY_CLASS_DEFINITION = TextAttributesKey.of("PY.CLASS_DEFINITION", CLASS_NAME);

  public static final TextAttributesKey PY_FUNC_DEFINITION = TextAttributesKey.of("PY.FUNC_DEFINITION", FUNCTION_DECLARATION);

  public static final TextAttributesKey PY_NESTED_FUNC_DEFINITION = TextAttributesKey.of("PY.NESTED_FUNC_DEFINITION", PY_FUNC_DEFINITION);

  public static final TextAttributesKey PY_PREDEFINED_DEFINITION = TextAttributesKey.of("PY.PREDEFINED_DEFINITION", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_PREDEFINED_USAGE = TextAttributesKey.of("PY.PREDEFINED_USAGE", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_BUILTIN_NAME = TextAttributesKey.of("PY.BUILTIN_NAME", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_PARAMETER = TextAttributesKey.of("PY.PARAMETER", PARAMETER);
  public static final TextAttributesKey PY_SELF_PARAMETER = TextAttributesKey.of( "PY.SELF_PARAMETER", PARAMETER);

  public static final TextAttributesKey PY_KEYWORD_ARGUMENT = TextAttributesKey.of("PY.KEYWORD_ARGUMENT", PARAMETER);

  public static final TextAttributesKey PY_FUNCTION_CALL = TextAttributesKey.of("PY.FUNCTION_CALL", FUNCTION_CALL);
  public static final TextAttributesKey PY_METHOD_CALL = TextAttributesKey.of("PY.METHOD_CALL", PY_FUNCTION_CALL);

  public static final TextAttributesKey PY_ANNOTATION = TextAttributesKey.of("PY.ANNOTATION", IDENTIFIER);

  public static final TextAttributesKey PY_VALID_STRING_ESCAPE = TextAttributesKey.of("PY.VALID_STRING_ESCAPE", VALID_STRING_ESCAPE);

  public static final TextAttributesKey PY_INVALID_STRING_ESCAPE = TextAttributesKey.of("PY.INVALID_STRING_ESCAPE", INVALID_STRING_ESCAPE);
  
  public static final TextAttributesKey PY_FSTRING_FRAGMENT_BRACES = TextAttributesKey.of("PY.FSTRING_FRAGMENT_BRACES", VALID_STRING_ESCAPE);
  public static final TextAttributesKey PY_FSTRING_FRAGMENT_COLON = TextAttributesKey.of("PY.FSTRING_FRAGMENT_COLON", VALID_STRING_ESCAPE);
  public static final TextAttributesKey PY_FSTRING_FRAGMENT_TYPE_CONVERSION = TextAttributesKey.of("PY.FSTRING_FRAGMENT_TYPE_CONVERSION", VALID_STRING_ESCAPE);
  public static final TextAttributesKey PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR = TextAttributesKey.of("PY.FSTRING_FORMAT_SPEC_SPECIAL_CHAR", VALID_STRING_ESCAPE);
  public static final TextAttributesKey PY_FSTRING_FORMAT_SPEC_NUMBER = TextAttributesKey.of("PY.FSTRING_FORMAT_SPEC_NUMBER", NUMBER);
  public static final TextAttributesKey PY_LOCAL_VARIABLE = TextAttributesKey.of("PY.LOCAL_VARIABLE", LOCAL_VARIABLE);
  public static final TextAttributesKey PY_TYPE_PARAMETER = TextAttributesKey.of("PY.TYPE_PARAMETER", PARAMETER);

  /**
   * The 'heavy' constructor that initializes everything. PySyntaxHighlighterFactory caches such instances per level.
   */
  public PyHighlighter(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    keys = new HashMap<>();

    fillMap(keys, PythonDialectsTokenSetProvider.getInstance().getKeywordTokens(), PY_KEYWORD);
    fillMap(keys, PyTokenTypes.OPERATIONS, PY_OPERATION_SIGN);

    keys.put(PyTokenTypes.INTEGER_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.FLOAT_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.IMAGINARY_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.SINGLE_QUOTED_STRING, PY_BYTE_STRING);
    keys.put(PyTokenTypes.TRIPLE_QUOTED_STRING, PY_BYTE_STRING);
    keys.put(PyTokenTypes.SINGLE_QUOTED_UNICODE, PY_UNICODE_STRING);
    keys.put(PyTokenTypes.TRIPLE_QUOTED_UNICODE, PY_UNICODE_STRING);

    keys.put(PyTokenTypes.FSTRING_START, PY_UNICODE_STRING);
    keys.put(PyTokenTypes.FSTRING_END, PY_UNICODE_STRING);
    keys.put(PyTokenTypes.FSTRING_TEXT, PY_UNICODE_STRING);
    keys.put(PyTokenTypes.FSTRING_RAW_TEXT, PY_UNICODE_STRING);

    keys.put(PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION, PY_FSTRING_FRAGMENT_TYPE_CONVERSION);
    keys.put(PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START, PY_FSTRING_FRAGMENT_COLON);
    keys.put(PyTokenTypes.FSTRING_FRAGMENT_START, PY_FSTRING_FRAGMENT_BRACES);
    keys.put(PyTokenTypes.FSTRING_FRAGMENT_END, PY_FSTRING_FRAGMENT_BRACES);

    keys.put(PyTokenTypes.DOCSTRING, PY_DOC_COMMENT);

    keys.put(PyTokenTypes.LPAR, PY_PARENTHS);
    keys.put(PyTokenTypes.RPAR, PY_PARENTHS);

    keys.put(PyTokenTypes.LBRACE, PY_BRACES);
    keys.put(PyTokenTypes.RBRACE, PY_BRACES);

    keys.put(PyTokenTypes.LBRACKET, PY_BRACKETS);
    keys.put(PyTokenTypes.RBRACKET, PY_BRACKETS);

    keys.put(PyTokenTypes.COMMA, PY_COMMA);
    keys.put(PyTokenTypes.DOT, PY_DOT);

    keys.put(PyTokenTypes.END_OF_LINE_COMMENT, PY_LINE_COMMENT);
    keys.put(PyTokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    keys.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, PY_VALID_STRING_ESCAPE);
    keys.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, PY_INVALID_STRING_ESCAPE);
    keys.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, PY_INVALID_STRING_ESCAPE);
  }

  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(keys.get(tokenType));
  }
}
