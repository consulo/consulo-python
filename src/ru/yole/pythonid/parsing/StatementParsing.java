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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;

public class StatementParsing extends Parsing {
	private static final Logger LOG = Logger.getInstance("#ru.yole.pythonid.parsing.StatementParsing");

	protected StatementParsing(ParsingContext context) {
		super(context);
	}

	public void parseStatement(PsiBuilder builder) {
		while (builder.getTokenType() == this.PyTokenTypes.STATEMENT_BREAK) {
			builder.advanceLexer();
		}

		IElementType firstToken = builder.getTokenType();

		if (firstToken == null) {
			return;
		}
		if (firstToken == this.PyTokenTypes.WHILE_KEYWORD) {
			parseWhileStatement(builder);
			return;
		}
		if (firstToken == this.PyTokenTypes.IF_KEYWORD) {
			parseIfStatement(builder);
			return;
		}
		if (firstToken == this.PyTokenTypes.FOR_KEYWORD) {
			parseForStatement(builder);
			return;
		}
		if (firstToken == this.PyTokenTypes.TRY_KEYWORD) {
			parseTryStatement(builder);
			return;
		}
		if (firstToken == this.PyTokenTypes.DEF_KEYWORD) {
			getFunctionParser().parseFunctionDeclaration(builder);
			return;
		}
		if (firstToken == this.PyTokenTypes.AT) {
			getFunctionParser().parseDecoratedFunctionDeclaration(builder);
			return;
		}
		if (firstToken == this.PyTokenTypes.CLASS_KEYWORD) {
			parseClassDeclaration(builder);
			return;
		}

		parseSimpleStatement(builder, false);
	}

	private void parseSimpleStatement(PsiBuilder builder, boolean inSuite) {
		IElementType firstToken = builder.getTokenType();
		if (firstToken == null) {
			return;
		}
		if (firstToken == this.PyTokenTypes.PRINT_KEYWORD) {
			parsePrintStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.ASSERT_KEYWORD) {
			parseAssertStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.BREAK_KEYWORD) {
			parseKeywordStatement(builder, this.PyElementTypes.BREAK_STATEMENT, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.CONTINUE_KEYWORD) {
			parseKeywordStatement(builder, this.PyElementTypes.CONTINUE_STATEMENT, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.DEL_KEYWORD) {
			parseDelStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.EXEC_KEYWORD) {
			parseExecStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.GLOBAL_KEYWORD) {
			parseGlobalStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.IMPORT_KEYWORD) {
			parseImportStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.FROM_KEYWORD) {
			parseFromImportStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.PASS_KEYWORD) {
			parseKeywordStatement(builder, this.PyElementTypes.PASS_STATEMENT, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.RETURN_KEYWORD) {
			parseReturnStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.YIELD_KEYWORD) {
			parseYieldStatement(builder, inSuite);
			return;
		}
		if (firstToken == this.PyTokenTypes.RAISE_KEYWORD) {
			parseRaiseStatement(builder, inSuite);
			return;
		}
		PsiBuilder.Marker exprStatement = builder.mark();
		if (getExpressionParser().parseExpressionOptional(builder)) {
			IElementType statementType = this.PyElementTypes.EXPRESSION_STATEMENT;
			if (this.PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(builder.getTokenType())) {
				statementType = this.PyElementTypes.AUG_ASSIGNMENT_STATEMENT;
				builder.advanceLexer();
				getExpressionParser().parseExpression(builder);
			} else if (builder.getTokenType() == this.PyTokenTypes.EQ) {
				statementType = this.PyElementTypes.ASSIGNMENT_STATEMENT;
				exprStatement.rollbackTo();
				exprStatement = builder.mark();
				getExpressionParser().parseExpression(builder, false, true);
				LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.EQ);
				builder.advanceLexer();
				while (true) {
					PsiBuilder.Marker maybeExprMarker = builder.mark();
					if (!getExpressionParser().parseExpressionOptional(builder)) {
						maybeExprMarker.drop();
						builder.error("expression expected");
						break;
					}
					if (builder.getTokenType() == this.PyTokenTypes.EQ) {
						maybeExprMarker.rollbackTo();
						getExpressionParser().parseExpression(builder, false, true);
						LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.EQ);
						builder.advanceLexer();
					} else {
						maybeExprMarker.drop();
						break;
					}
				}
			}

			checkEndOfStatement(builder, inSuite);
			exprStatement.done(statementType);
			return;
		}

		exprStatement.drop();

		builder.advanceLexer();
		builder.error("statement expected, found " + firstToken.toString());
	}

	private void checkEndOfStatement(PsiBuilder builder, boolean inSuite) {
		if (builder.getTokenType() == this.PyTokenTypes.STATEMENT_BREAK) {
			builder.advanceLexer();
		} else if (builder.getTokenType() == this.PyTokenTypes.SEMICOLON) {
			if (!inSuite) {
				builder.advanceLexer();
				if (builder.getTokenType() == this.PyTokenTypes.STATEMENT_BREAK)
					builder.advanceLexer();
			}
		} else {
			if (builder.eof()) {
				return;
			}

			builder.error("end of statement expected");
		}
	}

	private void parsePrintStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.PRINT_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		if (builder.getTokenType() == this.PyTokenTypes.GTGT) {
			PsiBuilder.Marker target = builder.mark();
			builder.advanceLexer();
			getExpressionParser().parseSingleExpression(builder, false);
			target.done(this.PyElementTypes.PRINT_TARGET);
		} else {
			getExpressionParser().parseSingleExpression(builder, false);
		}
		while (builder.getTokenType() == this.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			if (this.PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
				break;
			}
			getExpressionParser().parseSingleExpression(builder, false);
		}
		checkEndOfStatement(builder, inSuite);
		statement.done(this.PyElementTypes.PRINT_STATEMENT);
	}

	private void parseKeywordStatement(PsiBuilder builder, IElementType statementType, boolean inSuite) {
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		checkEndOfStatement(builder, inSuite);
		statement.done(statementType);
	}

	private void parseReturnStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.RETURN_KEYWORD);
		PsiBuilder.Marker returnStatement = builder.mark();
		builder.advanceLexer();
		if (!this.PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
			getExpressionParser().parseExpression(builder);
		}
		checkEndOfStatement(builder, inSuite);
		returnStatement.done(this.PyElementTypes.RETURN_STATEMENT);
	}

	private void parseYieldStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.YIELD_KEYWORD);
		PsiBuilder.Marker yieldStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder);
		checkEndOfStatement(builder, inSuite);
		yieldStatement.done(this.PyElementTypes.YIELD_STATEMENT);
	}

	private void parseDelStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.DEL_KEYWORD);
		PsiBuilder.Marker delStatement = builder.mark();
		builder.advanceLexer();
		if (!getExpressionParser().parseSingleExpression(builder, false)) {
			builder.error("expression expected");
		}
		while (builder.getTokenType() == this.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			if ((!this.PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) &&
					(!getExpressionParser().parseSingleExpression(builder, false))) {
				builder.error("expression expected");
			}

		}

		checkEndOfStatement(builder, inSuite);
		delStatement.done(this.PyElementTypes.DEL_STATEMENT);
	}

	private void parseRaiseStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.RAISE_KEYWORD);
		PsiBuilder.Marker raiseStatement = builder.mark();
		builder.advanceLexer();
		if (!this.PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
			getExpressionParser().parseSingleExpression(builder, false);
			if (builder.getTokenType() == this.PyTokenTypes.COMMA) {
				builder.advanceLexer();
				getExpressionParser().parseSingleExpression(builder, false);
				if (builder.getTokenType() == this.PyTokenTypes.COMMA) {
					builder.advanceLexer();
					getExpressionParser().parseSingleExpression(builder, false);
				}
			}
		}
		checkEndOfStatement(builder, inSuite);
		raiseStatement.done(this.PyElementTypes.RAISE_STATEMENT);
	}

	private void parseAssertStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.ASSERT_KEYWORD);
		PsiBuilder.Marker assertStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseSingleExpression(builder, false);
		if (builder.getTokenType() == this.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			getExpressionParser().parseSingleExpression(builder, false);
		}
		checkEndOfStatement(builder, inSuite);
		assertStatement.done(this.PyElementTypes.ASSERT_STATEMENT);
	}

	private void parseImportStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.IMPORT_KEYWORD);
		PsiBuilder.Marker importStatement = builder.mark();
		builder.advanceLexer();
		parseImportElements(builder, true, false);
		checkEndOfStatement(builder, inSuite);
		importStatement.done(this.PyElementTypes.IMPORT_STATEMENT);
	}

	private void parseFromImportStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.FROM_KEYWORD);
		PsiBuilder.Marker fromImportStatement = builder.mark();
		builder.advanceLexer();
		if (parseDottedName(builder)) {
			checkMatches(builder, this.PyTokenTypes.IMPORT_KEYWORD, "'import' expected");
			if (builder.getTokenType() == this.PyTokenTypes.MULT) {
				builder.advanceLexer();
			} else if (builder.getTokenType() == this.PyTokenTypes.LPAR) {
				builder.advanceLexer();
				parseImportElements(builder, false, true);
				checkMatches(builder, this.PyTokenTypes.RPAR, ") expected");
			} else {
				parseImportElements(builder, false, false);
			}
		}
		checkEndOfStatement(builder, inSuite);
		fromImportStatement.done(this.PyElementTypes.FROM_IMPORT_STATEMENT);
	}

	private void parseImportElements(PsiBuilder builder, boolean isModuleName, boolean inParens) {
		while (true) {
			PsiBuilder.Marker asMarker = builder.mark();
			if (isModuleName) {
				if (!parseDottedName(builder)) {
					asMarker.drop();
					break;
				}
			} else {
				parseReferenceExpression(builder);
			}
			String tokenText = builder.getTokenText();
			if ((builder.getTokenType() == this.PyTokenTypes.IDENTIFIER) && (tokenText != null) && (tokenText.equals("as"))) {
				builder.advanceLexer();
				parseReferenceExpression(builder);
			}
			asMarker.done(this.PyElementTypes.IMPORT_ELEMENT);
			if (builder.getTokenType() != this.PyTokenTypes.COMMA) break;
			builder.advanceLexer();
			if ((inParens) && (builder.getTokenType() == this.PyTokenTypes.RPAR))
				break;
		}
	}

	private void parseReferenceExpression(PsiBuilder builder) {
		PsiBuilder.Marker idMarker = builder.mark();
		if (builder.getTokenType() == this.PyTokenTypes.IDENTIFIER) {
			builder.advanceLexer();
			idMarker.done(this.PyElementTypes.REFERENCE_EXPRESSION);
		} else {
			builder.error("identifier expected");
			idMarker.drop();
		}
	}

	public boolean parseDottedName(PsiBuilder builder) {
		if (builder.getTokenType() != this.PyTokenTypes.IDENTIFIER) {
			builder.error("identifier expected");
			return false;
		}
		PsiBuilder.Marker marker = builder.mark();
		builder.advanceLexer();
		marker.done(this.PyElementTypes.REFERENCE_EXPRESSION);
		while (builder.getTokenType() == this.PyTokenTypes.DOT) {
			marker = marker.precede();
			builder.advanceLexer();
			checkMatches(builder, this.PyTokenTypes.IDENTIFIER, "identifier expected");
			marker.done(this.PyElementTypes.REFERENCE_EXPRESSION);
		}
		return true;
	}

	private void parseGlobalStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.GLOBAL_KEYWORD);
		PsiBuilder.Marker globalStatement = builder.mark();
		builder.advanceLexer();
		parseReferenceExpression(builder);
		while (builder.getTokenType() == this.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			parseReferenceExpression(builder);
		}
		checkEndOfStatement(builder, inSuite);
		globalStatement.done(this.PyElementTypes.GLOBAL_STATEMENT);
	}

	private void parseExecStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.EXEC_KEYWORD);
		PsiBuilder.Marker execStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder, true, false);
		if (builder.getTokenType() == this.PyTokenTypes.IN_KEYWORD) {
			builder.advanceLexer();
			getExpressionParser().parseSingleExpression(builder, false);
			if (builder.getTokenType() == this.PyTokenTypes.COMMA) {
				builder.advanceLexer();
				getExpressionParser().parseSingleExpression(builder, false);
			}
		}
		checkEndOfStatement(builder, inSuite);
		execStatement.done(this.PyElementTypes.EXEC_STATEMENT);
	}

	private void parseIfStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.IF_KEYWORD);
		PsiBuilder.Marker ifStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder);
		checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		while (builder.getTokenType() == this.PyTokenTypes.ELIF_KEYWORD) {
			builder.advanceLexer();
			getExpressionParser().parseExpression(builder);
			checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		if (builder.getTokenType() == this.PyTokenTypes.ELSE_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		ifStatement.done(this.PyElementTypes.IF_STATEMENT);
	}

	private void parseForStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.FOR_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder, true, true);
		checkMatches(builder, this.PyTokenTypes.IN_KEYWORD, "'in' expected");
		getExpressionParser().parseExpression(builder);
		checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		if (builder.getTokenType() == this.PyTokenTypes.ELSE_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		statement.done(this.PyElementTypes.FOR_STATEMENT);
	}

	private void parseWhileStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.WHILE_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder);
		checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		if (builder.getTokenType() == this.PyTokenTypes.ELSE_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		statement.done(this.PyElementTypes.WHILE_STATEMENT);
	}

	private void parseTryStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.TRY_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		if (builder.getTokenType() == this.PyTokenTypes.EXCEPT_KEYWORD) {
			while (builder.getTokenType() == this.PyTokenTypes.EXCEPT_KEYWORD) {
				PsiBuilder.Marker exceptBlock = builder.mark();
				builder.advanceLexer();
				if (builder.getTokenType() != this.PyTokenTypes.COLON) {
					if (!getExpressionParser().parseSingleExpression(builder, false)) {
						builder.error("expression expected");
					}
					if (builder.getTokenType() == this.PyTokenTypes.COMMA) {
						builder.advanceLexer();
						if (!getExpressionParser().parseSingleExpression(builder, true)) {
							builder.error("expression expected");
						}
					}
				}
				checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
				parseSuite(builder);
				exceptBlock.done(this.PyElementTypes.EXCEPT_BLOCK);
			}
			if (builder.getTokenType() == this.PyTokenTypes.ELSE_KEYWORD) {
				builder.advanceLexer();
				checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
				parseSuite(builder);
			}
			statement.done(this.PyElementTypes.TRY_EXCEPT_STATEMENT);
		} else if (builder.getTokenType() == this.PyTokenTypes.FINALLY_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
			statement.done(this.PyElementTypes.TRY_FINALLY_STATEMENT);
		} else {
			builder.error("'except' or 'finally' expected");

			statement.done(this.PyElementTypes.TRY_FINALLY_STATEMENT);
		}
	}

	private void parseClassDeclaration(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == this.PyTokenTypes.CLASS_KEYWORD);
		PsiBuilder.Marker classMarker = builder.mark();
		builder.advanceLexer();
		checkMatches(builder, this.PyTokenTypes.IDENTIFIER, "identifier expected");
		PsiBuilder.Marker inheritMarker = builder.mark();
		if (builder.getTokenType() == this.PyTokenTypes.LPAR) {
			builder.advanceLexer();
			getExpressionParser().parseExpression(builder);
			checkMatches(builder, this.PyTokenTypes.RPAR, ") expected");
		}
		inheritMarker.done(this.PyElementTypes.PARENTHESIZED_EXPRESSION);
		checkMatches(builder, this.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		classMarker.done(this.PyElementTypes.CLASS_DECLARATION);
	}

	public void parseSuite(PsiBuilder builder) {
		parseSuite(builder, null, null);
	}

	public void parseSuite(PsiBuilder builder, PsiBuilder.Marker endMarker, IElementType elType) {
		if (builder.getTokenType() == this.PyTokenTypes.STATEMENT_BREAK) {
			builder.advanceLexer();

			PsiBuilder.Marker marker = builder.mark();
			if (builder.getTokenType() != this.PyTokenTypes.INDENT) {
				builder.error("indent expected");
			} else {
				builder.advanceLexer();
				while ((!builder.eof()) && (builder.getTokenType() != this.PyTokenTypes.DEDENT)) {
					parseStatement(builder);
				}
			}

			marker.done(this.PyElementTypes.STATEMENT_LIST);
			if (endMarker != null) {
				endMarker.done(elType);
			}
			if (!builder.eof()) {
				checkMatches(builder, this.PyTokenTypes.DEDENT, "dedent expected");
			}

			builder.getTokenType();
		} else {
			PsiBuilder.Marker marker = builder.mark();
			parseSimpleStatement(builder, true);
			while (builder.getTokenType() == this.PyTokenTypes.SEMICOLON) {
				builder.advanceLexer();
				parseSimpleStatement(builder, true);
			}
			marker.done(this.PyElementTypes.STATEMENT_LIST);
			if (endMarker != null)
				endMarker.done(elType);
		}
	}
}