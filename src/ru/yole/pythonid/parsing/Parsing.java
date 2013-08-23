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

package ru.yole.pythonid.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import ru.yole.pythonid.PyElementTypes;
import ru.yole.pythonid.PyTokenTypes;

public class Parsing {
	protected PyTokenTypes PyTokenTypes;
	protected PyElementTypes PyElementTypes;
	private ParsingContext context;

	protected Parsing(ParsingContext context) {
		this.context = context;
		this.PyTokenTypes = context.getLanguage().getTokenTypes();
		this.PyElementTypes = context.getLanguage().getElementTypes();
	}

	public ParsingContext getParsingContext() {
		return this.context;
	}

	public ExpressionParsing getExpressionParser() {
		return getParsingContext().getExpressionParser();
	}

	public StatementParsing getStatementParser() {
		return getParsingContext().getStatementParser();
	}

	public FunctionParsing getFunctionParser() {
		return getParsingContext().getFunctionParser();
	}

	protected static void checkMatches(PsiBuilder builder, IElementType token, String message) {
		if (builder.getTokenType() == token) {
			builder.advanceLexer();
		} else
			builder.error(message);
	}

	protected static void checkMatches(PsiBuilder builder, TokenSet tokenSet, String message) {
		if (tokenSet.contains(builder.getTokenType())) {
			builder.advanceLexer();
		} else
			builder.error(message);
	}
}