/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package org.consulo.python;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class PyHighligher extends SyntaxHighlighterBase {
	private static Map<IElementType, TextAttributesKey> keys1;

	public static final TextAttributesKey PY_KEYWORD = TextAttributesKey.createTextAttributesKey("PY.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

	public static final TextAttributesKey PY_STRING = TextAttributesKey.createTextAttributesKey("PY.STRING", DefaultLanguageHighlighterColors.STRING);

	public static final TextAttributesKey PY_NUMBER = TextAttributesKey.createTextAttributesKey("PY.NUMBER", DefaultLanguageHighlighterColors.NUMBER);

	public static final TextAttributesKey PY_LINE_COMMENT = TextAttributesKey.createTextAttributesKey("PY.LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

	public static final TextAttributesKey PY_OPERATION_SIGN = TextAttributesKey.createTextAttributesKey("PY.OPERATION_SIGN", DefaultLanguageHighlighterColors.OPERATION_SIGN);

	public static final TextAttributesKey PY_PARENTHS = TextAttributesKey.createTextAttributesKey("PY.PARENTHS", DefaultLanguageHighlighterColors.PARENTHESES);

	public static final TextAttributesKey PY_BRACKETS = TextAttributesKey.createTextAttributesKey("PY.BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);

	public static final TextAttributesKey PY_BRACES = TextAttributesKey.createTextAttributesKey("PY.BRACES", DefaultLanguageHighlighterColors.BRACES);

	public static final TextAttributesKey PY_COMMA = TextAttributesKey.createTextAttributesKey("PY.COMMA", DefaultLanguageHighlighterColors.COMMA);

	public static final TextAttributesKey PY_DOT = TextAttributesKey.createTextAttributesKey("PY.DOT", DefaultLanguageHighlighterColors.DOT);

	@Override
	@NotNull
	public Lexer getHighlightingLexer() {
		return new PythonLexer();
	}

	public PyHighligher() {

		keys1 = new HashMap<IElementType, TextAttributesKey>();

		fillMap(keys1, PythonTokenSets.KEYWORDS, PY_KEYWORD);
		fillMap(keys1, PythonTokenSets.OPERATIONS, PY_OPERATION_SIGN);

		keys1.put(PyTokenTypes.INTEGER_LITERAL, PY_NUMBER);
		keys1.put(PyTokenTypes.FLOAT_LITERAL, PY_NUMBER);
		keys1.put(PyTokenTypes.IMAGINARY_LITERAL, PY_NUMBER);


		keys1.put(PyTokenTypes.LPAR, PY_PARENTHS);
		keys1.put(PyTokenTypes.RPAR, PY_PARENTHS);

		keys1.put(PyTokenTypes.LBRACE, PY_BRACES);
		keys1.put(PyTokenTypes.RBRACE, PY_BRACES);

		keys1.put(PyTokenTypes.LBRACKET, PY_BRACKETS);
		keys1.put(PyTokenTypes.RBRACKET, PY_BRACKETS);

		keys1.put(PyTokenTypes.COMMA, PY_COMMA);
		keys1.put(PyTokenTypes.DOT, PY_DOT);

		fillMap(keys1, PythonTokenSets.COMMENTS, PY_LINE_COMMENT);
		fillMap(keys1, PythonTokenSets.STRINGS, PY_STRING);
		keys1.put(PyTokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
	}

	@Override
	@NotNull
	public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
		return pack(keys1.get(tokenType));
	}
}