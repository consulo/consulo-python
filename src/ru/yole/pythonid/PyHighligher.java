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

package ru.yole.pythonid;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class PyHighligher extends SyntaxHighlighterBase {
	private static Map<IElementType, TextAttributesKey> keys1;
	private PythonLanguage language;
	final TextAttributesKey PY_KEYWORD = TextAttributesKey.createTextAttributesKey("PY.KEYWORD", HighlighterColors.JAVA_KEYWORD.getDefaultAttributes());

	final TextAttributesKey PY_STRING = TextAttributesKey.createTextAttributesKey("PY.STRING", HighlighterColors.JAVA_STRING.getDefaultAttributes());

	final TextAttributesKey PY_NUMBER = TextAttributesKey.createTextAttributesKey("PY.NUMBER", HighlighterColors.JAVA_NUMBER.getDefaultAttributes());

	final TextAttributesKey PY_LINE_COMMENT = TextAttributesKey.createTextAttributesKey("PY.LINE_COMMENT", HighlighterColors.JAVA_LINE_COMMENT.getDefaultAttributes());

	final TextAttributesKey PY_OPERATION_SIGN = TextAttributesKey.createTextAttributesKey("PY.OPERATION_SIGN", HighlighterColors.JAVA_OPERATION_SIGN.getDefaultAttributes());

	final TextAttributesKey PY_PARENTHS = TextAttributesKey.createTextAttributesKey("PY.PARENTHS", HighlighterColors.JAVA_PARENTHS.getDefaultAttributes());

	final TextAttributesKey PY_BRACKETS = TextAttributesKey.createTextAttributesKey("PY.BRACKETS", HighlighterColors.JAVA_BRACKETS.getDefaultAttributes());

	final TextAttributesKey PY_BRACES = TextAttributesKey.createTextAttributesKey("PY.BRACES", HighlighterColors.JAVA_BRACES.getDefaultAttributes());

	final TextAttributesKey PY_COMMA = TextAttributesKey.createTextAttributesKey("PY.COMMA", HighlighterColors.JAVA_COMMA.getDefaultAttributes());

	final TextAttributesKey PY_DOT = TextAttributesKey.createTextAttributesKey("PY.DOT", HighlighterColors.JAVA_DOT.getDefaultAttributes());

	@NotNull
	public Lexer getHighlightingLexer() {
		void tmp11_8 = new PythonLexer(this.language);
		if (tmp11_8 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp11_8;
	}

	public PyHighligher(PythonLanguage language) {
		this.language = language;
		PyTokenTypes tokenTypes = language.getTokenTypes();
		keys1 = new HashMap();

		fillMap(keys1, tokenTypes.KEYWORDS, this.PY_KEYWORD);
		fillMap(keys1, tokenTypes.OPERATIONS, this.PY_OPERATION_SIGN);

		keys1.put(tokenTypes.INTEGER_LITERAL, this.PY_NUMBER);
		keys1.put(tokenTypes.FLOAT_LITERAL, this.PY_NUMBER);
		keys1.put(tokenTypes.IMAGINARY_LITERAL, this.PY_NUMBER);
		keys1.put(tokenTypes.STRING_LITERAL, this.PY_STRING);

		keys1.put(tokenTypes.LPAR, this.PY_PARENTHS);
		keys1.put(tokenTypes.RPAR, this.PY_PARENTHS);

		keys1.put(tokenTypes.LBRACE, this.PY_BRACES);
		keys1.put(tokenTypes.RBRACE, this.PY_BRACES);

		keys1.put(tokenTypes.LBRACKET, this.PY_BRACKETS);
		keys1.put(tokenTypes.RBRACKET, this.PY_BRACKETS);

		keys1.put(tokenTypes.COMMA, this.PY_COMMA);
		keys1.put(tokenTypes.DOT, this.PY_DOT);

		keys1.put(tokenTypes.END_OF_LINE_COMMENT, this.PY_LINE_COMMENT);
		keys1.put(tokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
	}

	@NotNull
	public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
		TextAttributesKey[] tmp15_12 = pack((TextAttributesKey) keys1.get(tokenType));
		if (tmp15_12 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp15_12;
	}
}