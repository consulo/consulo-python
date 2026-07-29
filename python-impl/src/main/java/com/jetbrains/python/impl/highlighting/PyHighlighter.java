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
package com.jetbrains.python.impl.highlighting;

import consulo.language.ast.StringEscapesTokenTypes;
import consulo.language.lexer.LayeredLexer;
import consulo.language.lexer.Lexer;
import consulo.codeEditor.HighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.lexer.PyStringLiteralLexer;
import com.jetbrains.python.impl.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.HashMap;
import java.util.Map;

import static consulo.codeEditor.DefaultLanguageHighlighterColors.*;

/**
 * Colors and lexer(s) needed for highlighting.
 */
public class PyHighlighter extends SyntaxHighlighterBase {
  private Map<IElementType, TextAttributesKey> keys;
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

    return ret;
  }

  protected PythonHighlightingLexer createHighlightingLexer(LanguageLevel languageLevel) {
    return new PythonHighlightingLexer(myLanguageLevel);
  }

  public static final TextAttributesKey PY_KEYWORD = TextAttributesKey.of("PY.KEYWORD", KEYWORD);

  public static final TextAttributesKey PY_BYTE_STRING = TextAttributesKey.of("PY.STRING.B", STRING);
  public static final TextAttributesKey PY_UNICODE_STRING = TextAttributesKey.of("PY.STRING.U", STRING);
  public static final TextAttributesKey PY_NUMBER = TextAttributesKey.of("PY.NUMBER", NUMBER);

  static final TextAttributesKey PY_OPERATION_SIGN = TextAttributesKey.of("PY.OPERATION_SIGN", OPERATION_SIGN);

  static final TextAttributesKey PY_PARENTHS = TextAttributesKey.of("PY.PARENTHS", PARENTHESES);

  static final TextAttributesKey PY_BRACKETS = TextAttributesKey.of("PY.BRACKETS", BRACKETS);

  static final TextAttributesKey PY_BRACES = TextAttributesKey.of("PY.BRACES", BRACES);

  static final TextAttributesKey PY_COMMA = TextAttributesKey.of("PY.COMMA", COMMA);

  static final TextAttributesKey PY_DOT = TextAttributesKey.of("PY.DOT", DOT);

  public static final TextAttributesKey PY_LINE_COMMENT = TextAttributesKey.of("PY.LINE_COMMENT", LINE_COMMENT);

  public static final TextAttributesKey PY_DOC_COMMENT = TextAttributesKey.of("PY.DOC_COMMENT", DOC_COMMENT);

  public static final TextAttributesKey PY_DOC_COMMENT_TAG = TextAttributesKey.of("PY.DOC_COMMENT_TAG", DOC_COMMENT_TAG);

  public static final TextAttributesKey PY_DECORATOR = TextAttributesKey.of("PY.DECORATOR", IDENTIFIER);

  public static final TextAttributesKey PY_CLASS_DEFINITION = TextAttributesKey.of("PY.CLASS_DEFINITION", CLASS_NAME);

  public static final TextAttributesKey PY_FUNC_DEFINITION = TextAttributesKey.of("PY.FUNC_DEFINITION", FUNCTION_DECLARATION);

  public static final TextAttributesKey PY_PREDEFINED_DEFINITION = TextAttributesKey.of("PY.PREDEFINED_DEFINITION", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_PREDEFINED_USAGE = TextAttributesKey.of("PY.PREDEFINED_USAGE", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_BUILTIN_NAME = TextAttributesKey.of("PY.BUILTIN_NAME", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_PARAMETER = TextAttributesKey.of("PY.PARAMETER", PARAMETER);
  public static final TextAttributesKey PY_SELF_PARAMETER = TextAttributesKey.of( "PY.SELF_PARAMETER", PARAMETER);

  public static final TextAttributesKey PY_KEYWORD_ARGUMENT = TextAttributesKey.of("PY.KEYWORD_ARGUMENT", PARAMETER);

  public static final TextAttributesKey PY_VALID_STRING_ESCAPE = TextAttributesKey.of("PY.VALID_STRING_ESCAPE", VALID_STRING_ESCAPE);

  public static final TextAttributesKey PY_INVALID_STRING_ESCAPE = TextAttributesKey.of("PY.INVALID_STRING_ESCAPE", INVALID_STRING_ESCAPE);
  
  /**
   * The 'heavy' constructor that initializes everything. PySyntaxHighlighterFactory caches such instances per level.
   * @param languageLevel
   */
  public PyHighlighter(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    keys = new HashMap<>();

    fillMap(keys, PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens(), PY_KEYWORD);
    fillMap(keys, PyTokenTypes.OPERATIONS, PY_OPERATION_SIGN);

    keys.put(PyTokenTypes.INTEGER_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.FLOAT_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.IMAGINARY_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.SINGLE_QUOTED_STRING, PY_BYTE_STRING);
    keys.put(PyTokenTypes.TRIPLE_QUOTED_STRING, PY_BYTE_STRING);
    keys.put(PyTokenTypes.SINGLE_QUOTED_UNICODE, PY_UNICODE_STRING);
    keys.put(PyTokenTypes.TRIPLE_QUOTED_UNICODE, PY_UNICODE_STRING);

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
