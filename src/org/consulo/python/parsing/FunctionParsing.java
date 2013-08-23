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

package org.consulo.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;

public class FunctionParsing extends Parsing {
	private static final Logger LOG = Logger.getInstance("#ru.yole.pythonid.parsing.FunctionParsing");

	public FunctionParsing(ParsingContext context) {
		super(context);
	}

	public void parseFunctionDeclaration(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.DEF_KEYWORD);
		PsiBuilder.Marker functionMarker = builder.mark();
		builder.advanceLexer();
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.IDENTIFIER) {
			builder.advanceLexer();
		} else {
			builder.error("function name expected");
		}

		parseParameterList(builder);
		checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
		getStatementParser().parseSuite(builder, functionMarker, org.consulo.python.PyElementTypes.FUNCTION_DECLARATION);
	}

	public void parseDecoratedFunctionDeclaration(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.AT);
		PsiBuilder.Marker functionMarker = builder.mark();
		builder.advanceLexer();
		getStatementParser().parseDottedName(builder);
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.LPAR) {
			getExpressionParser().parseArgumentList(builder);
		}
		checkMatches(builder, org.consulo.python.PyTokenTypes.STATEMENT_BREAK, "statement break expected");
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.AT) {
			parseDecoratedFunctionDeclaration(builder);
		} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.DEF_KEYWORD) {
			parseFunctionDeclaration(builder);
		} else {
			builder.error("'def' or '@' expected");
		}
		functionMarker.done(org.consulo.python.PyElementTypes.DECORATED_FUNCTION_DECLARATION);
	}

	private void parseParameterList(PsiBuilder builder) {
		if (builder.getTokenType() != org.consulo.python.PyTokenTypes.LPAR) {
			builder.error("( expected");
			PsiBuilder.Marker parameterList = builder.mark();
			parameterList.done(org.consulo.python.PyElementTypes.PARAMETER_LIST);
			return;
		}
		parseParameterListContents(builder, org.consulo.python.PyTokenTypes.RPAR, true);
	}

	public void parseParameterListContents(PsiBuilder builder, IElementType endToken, boolean advanceLexer) {
		PsiBuilder.Marker parameterList = builder.mark();
		if (advanceLexer) {
			builder.advanceLexer();
		}

		boolean first = true;
		while (builder.getTokenType() != endToken) {
			if (first) {
				first = false;
			} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
				builder.advanceLexer();
			} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.LPAR) {
				parseParameterSubList(builder);
			} else {
				builder.error(", or ( or ) expected");
				break;
			}

			PsiBuilder.Marker parameter = builder.mark();
			boolean isStarParameter = false;
			if ((builder.getTokenType() == org.consulo.python.PyTokenTypes.MULT) || (builder.getTokenType() == org.consulo.python.PyTokenTypes.EXP)) {
				builder.advanceLexer();
				isStarParameter = true;
			}
			if (builder.getTokenType() == org.consulo.python.PyTokenTypes.IDENTIFIER) {
				builder.advanceLexer();
				if ((builder.getTokenType() == org.consulo.python.PyTokenTypes.EQ) && (!isStarParameter)) {
					builder.advanceLexer();
					getExpressionParser().parseSingleExpression(builder, false);
				}
				parameter.done(org.consulo.python.PyElementTypes.FORMAL_PARAMETER);
			} else {
				builder.error("formal parameter name expected");
				parameter.rollbackTo();
			}
		}

		if (builder.getTokenType() == endToken) {
			builder.advanceLexer();
		}

		parameterList.done(org.consulo.python.PyElementTypes.PARAMETER_LIST);
	}

	private void parseParameterSubList(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.LPAR);
		builder.advanceLexer();
		while (true) {
			if (builder.getTokenType() == org.consulo.python.PyTokenTypes.IDENTIFIER) {
				PsiBuilder.Marker parameter = builder.mark();
				builder.advanceLexer();
				parameter.done(org.consulo.python.PyElementTypes.FORMAL_PARAMETER);
			} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.LPAR) {
				parseParameterSubList(builder);
			}
			if (builder.getTokenType() == org.consulo.python.PyTokenTypes.RPAR) {
				builder.advanceLexer();
				break;
			}
			if (builder.getTokenType() != org.consulo.python.PyTokenTypes.COMMA) {
				builder.error(", or ( or ) expected");
				break;
			}
			builder.advanceLexer();
		}
	}
}