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
import org.consulo.python.PythonTokenSets;

public class StatementParsing extends Parsing {
	private static final Logger LOG = Logger.getInstance("#ru.yole.pythonid.parsing.StatementParsing");

	protected StatementParsing(ParsingContext context) {
		super(context);
	}

	public void parseStatement(PsiBuilder builder) {
		while (builder.getTokenType() == org.consulo.python.PyTokenTypes.STATEMENT_BREAK) {
			builder.advanceLexer();
		}

		IElementType firstToken = builder.getTokenType();

		if (firstToken == null) {
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.WHILE_KEYWORD) {
			parseWhileStatement(builder);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.IF_KEYWORD) {
			parseIfStatement(builder);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.FOR_KEYWORD) {
			parseForStatement(builder);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.TRY_KEYWORD) {
			parseTryStatement(builder);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.DEF_KEYWORD) {
			getFunctionParser().parseFunctionDeclaration(builder);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.AT) {
			getFunctionParser().parseDecoratedFunctionDeclaration(builder);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.CLASS_KEYWORD) {
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
		if (firstToken == org.consulo.python.PyTokenTypes.PRINT_KEYWORD) {
			parsePrintStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.ASSERT_KEYWORD) {
			parseAssertStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.BREAK_KEYWORD) {
			parseKeywordStatement(builder, org.consulo.python.PyElementTypes.BREAK_STATEMENT, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.CONTINUE_KEYWORD) {
			parseKeywordStatement(builder, org.consulo.python.PyElementTypes.CONTINUE_STATEMENT, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.DEL_KEYWORD) {
			parseDelStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.EXEC_KEYWORD) {
			parseExecStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.GLOBAL_KEYWORD) {
			parseGlobalStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.IMPORT_KEYWORD) {
			parseImportStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.FROM_KEYWORD) {
			parseFromImportStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.PASS_KEYWORD) {
			parseKeywordStatement(builder, org.consulo.python.PyElementTypes.PASS_STATEMENT, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.RETURN_KEYWORD) {
			parseReturnStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.YIELD_KEYWORD) {
			parseYieldStatement(builder, inSuite);
			return;
		}
		if (firstToken == org.consulo.python.PyTokenTypes.RAISE_KEYWORD) {
			parseRaiseStatement(builder, inSuite);
			return;
		}
		PsiBuilder.Marker exprStatement = builder.mark();
		if (getExpressionParser().parseExpressionOptional(builder)) {
			IElementType statementType = org.consulo.python.PyElementTypes.EXPRESSION_STATEMENT;
			if (PythonTokenSets.AUG_ASSIGN_OPERATIONS.contains(builder.getTokenType())) {
				statementType = org.consulo.python.PyElementTypes.AUG_ASSIGNMENT_STATEMENT;
				builder.advanceLexer();
				getExpressionParser().parseExpression(builder);
			} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.EQ) {
				statementType = org.consulo.python.PyElementTypes.ASSIGNMENT_STATEMENT;
				exprStatement.rollbackTo();
				exprStatement = builder.mark();
				getExpressionParser().parseExpression(builder, false, true);
				LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.EQ);
				builder.advanceLexer();
				while (true) {
					PsiBuilder.Marker maybeExprMarker = builder.mark();
					if (!getExpressionParser().parseExpressionOptional(builder)) {
						maybeExprMarker.drop();
						builder.error("expression expected");
						break;
					}
					if (builder.getTokenType() == org.consulo.python.PyTokenTypes.EQ) {
						maybeExprMarker.rollbackTo();
						getExpressionParser().parseExpression(builder, false, true);
						LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.EQ);
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
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.STATEMENT_BREAK) {
			builder.advanceLexer();
		} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.SEMICOLON) {
			if (!inSuite) {
				builder.advanceLexer();
				if (builder.getTokenType() == org.consulo.python.PyTokenTypes.STATEMENT_BREAK)
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
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.PRINT_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.GTGT) {
			PsiBuilder.Marker target = builder.mark();
			builder.advanceLexer();
			getExpressionParser().parseSingleExpression(builder, false);
			target.done(org.consulo.python.PyElementTypes.PRINT_TARGET);
		} else {
			getExpressionParser().parseSingleExpression(builder, false);
		}
		while (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			if (PythonTokenSets.END_OF_STATEMENT.contains(builder.getTokenType())) {
				break;
			}
			getExpressionParser().parseSingleExpression(builder, false);
		}
		checkEndOfStatement(builder, inSuite);
		statement.done(org.consulo.python.PyElementTypes.PRINT_STATEMENT);
	}

	private void parseKeywordStatement(PsiBuilder builder, IElementType statementType, boolean inSuite) {
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		checkEndOfStatement(builder, inSuite);
		statement.done(statementType);
	}

	private void parseReturnStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.RETURN_KEYWORD);
		PsiBuilder.Marker returnStatement = builder.mark();
		builder.advanceLexer();
		if (!PythonTokenSets.END_OF_STATEMENT.contains(builder.getTokenType())) {
			getExpressionParser().parseExpression(builder);
		}
		checkEndOfStatement(builder, inSuite);
		returnStatement.done(org.consulo.python.PyElementTypes.RETURN_STATEMENT);
	}

	private void parseYieldStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.YIELD_KEYWORD);
		PsiBuilder.Marker yieldStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder);
		checkEndOfStatement(builder, inSuite);
		yieldStatement.done(org.consulo.python.PyElementTypes.YIELD_STATEMENT);
	}

	private void parseDelStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.DEL_KEYWORD);
		PsiBuilder.Marker delStatement = builder.mark();
		builder.advanceLexer();
		if (!getExpressionParser().parseSingleExpression(builder, false)) {
			builder.error("expression expected");
		}
		while (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			if ((!PythonTokenSets.END_OF_STATEMENT.contains(builder.getTokenType())) &&
					(!getExpressionParser().parseSingleExpression(builder, false))) {
				builder.error("expression expected");
			}

		}

		checkEndOfStatement(builder, inSuite);
		delStatement.done(org.consulo.python.PyElementTypes.DEL_STATEMENT);
	}

	private void parseRaiseStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.RAISE_KEYWORD);
		PsiBuilder.Marker raiseStatement = builder.mark();
		builder.advanceLexer();
		if (!PythonTokenSets.END_OF_STATEMENT.contains(builder.getTokenType())) {
			getExpressionParser().parseSingleExpression(builder, false);
			if (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
				builder.advanceLexer();
				getExpressionParser().parseSingleExpression(builder, false);
				if (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
					builder.advanceLexer();
					getExpressionParser().parseSingleExpression(builder, false);
				}
			}
		}
		checkEndOfStatement(builder, inSuite);
		raiseStatement.done(org.consulo.python.PyElementTypes.RAISE_STATEMENT);
	}

	private void parseAssertStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.ASSERT_KEYWORD);
		PsiBuilder.Marker assertStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseSingleExpression(builder, false);
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			getExpressionParser().parseSingleExpression(builder, false);
		}
		checkEndOfStatement(builder, inSuite);
		assertStatement.done(org.consulo.python.PyElementTypes.ASSERT_STATEMENT);
	}

	private void parseImportStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.IMPORT_KEYWORD);
		PsiBuilder.Marker importStatement = builder.mark();
		builder.advanceLexer();
		parseImportElements(builder, true, false);
		checkEndOfStatement(builder, inSuite);
		importStatement.done(org.consulo.python.PyElementTypes.IMPORT_STATEMENT);
	}

	private void parseFromImportStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.FROM_KEYWORD);
		PsiBuilder.Marker fromImportStatement = builder.mark();
		builder.advanceLexer();
		if (parseDottedName(builder)) {
			checkMatches(builder, org.consulo.python.PyTokenTypes.IMPORT_KEYWORD, "'import' expected");
			if (builder.getTokenType() == org.consulo.python.PyTokenTypes.MULT) {
				builder.advanceLexer();
			} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.LPAR) {
				builder.advanceLexer();
				parseImportElements(builder, false, true);
				checkMatches(builder, org.consulo.python.PyTokenTypes.RPAR, ") expected");
			} else {
				parseImportElements(builder, false, false);
			}
		}
		checkEndOfStatement(builder, inSuite);
		fromImportStatement.done(org.consulo.python.PyElementTypes.FROM_IMPORT_STATEMENT);
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
			if ((builder.getTokenType() == org.consulo.python.PyTokenTypes.IDENTIFIER) && (tokenText != null) && (tokenText.equals("as"))) {
				builder.advanceLexer();
				parseReferenceExpression(builder);
			}
			asMarker.done(org.consulo.python.PyElementTypes.IMPORT_ELEMENT);
			if (builder.getTokenType() != org.consulo.python.PyTokenTypes.COMMA) break;
			builder.advanceLexer();
			if ((inParens) && (builder.getTokenType() == org.consulo.python.PyTokenTypes.RPAR))
				break;
		}
	}

	private void parseReferenceExpression(PsiBuilder builder) {
		PsiBuilder.Marker idMarker = builder.mark();
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.IDENTIFIER) {
			builder.advanceLexer();
			idMarker.done(org.consulo.python.PyElementTypes.REFERENCE_EXPRESSION);
		} else {
			builder.error("identifier expected");
			idMarker.drop();
		}
	}

	public boolean parseDottedName(PsiBuilder builder) {
		if (builder.getTokenType() != org.consulo.python.PyTokenTypes.IDENTIFIER) {
			builder.error("identifier expected");
			return false;
		}
		PsiBuilder.Marker marker = builder.mark();
		builder.advanceLexer();
		marker.done(org.consulo.python.PyElementTypes.REFERENCE_EXPRESSION);
		while (builder.getTokenType() == org.consulo.python.PyTokenTypes.DOT) {
			marker = marker.precede();
			builder.advanceLexer();
			checkMatches(builder, org.consulo.python.PyTokenTypes.IDENTIFIER, "identifier expected");
			marker.done(org.consulo.python.PyElementTypes.REFERENCE_EXPRESSION);
		}
		return true;
	}

	private void parseGlobalStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.GLOBAL_KEYWORD);
		PsiBuilder.Marker globalStatement = builder.mark();
		builder.advanceLexer();
		parseReferenceExpression(builder);
		while (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
			builder.advanceLexer();
			parseReferenceExpression(builder);
		}
		checkEndOfStatement(builder, inSuite);
		globalStatement.done(org.consulo.python.PyElementTypes.GLOBAL_STATEMENT);
	}

	private void parseExecStatement(PsiBuilder builder, boolean inSuite) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.EXEC_KEYWORD);
		PsiBuilder.Marker execStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder, true, false);
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.IN_KEYWORD) {
			builder.advanceLexer();
			getExpressionParser().parseSingleExpression(builder, false);
			if (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
				builder.advanceLexer();
				getExpressionParser().parseSingleExpression(builder, false);
			}
		}
		checkEndOfStatement(builder, inSuite);
		execStatement.done(org.consulo.python.PyElementTypes.EXEC_STATEMENT);
	}

	private void parseIfStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.IF_KEYWORD);
		PsiBuilder.Marker ifStatement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder);
		checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		while (builder.getTokenType() == org.consulo.python.PyTokenTypes.ELIF_KEYWORD) {
			builder.advanceLexer();
			getExpressionParser().parseExpression(builder);
			checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.ELSE_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		ifStatement.done(org.consulo.python.PyElementTypes.IF_STATEMENT);
	}

	private void parseForStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.FOR_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder, true, true);
		checkMatches(builder, org.consulo.python.PyTokenTypes.IN_KEYWORD, "'in' expected");
		getExpressionParser().parseExpression(builder);
		checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.ELSE_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		statement.done(org.consulo.python.PyElementTypes.FOR_STATEMENT);
	}

	private void parseWhileStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.WHILE_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		getExpressionParser().parseExpression(builder);
		checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.ELSE_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
		}
		statement.done(org.consulo.python.PyElementTypes.WHILE_STATEMENT);
	}

	private void parseTryStatement(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.TRY_KEYWORD);
		PsiBuilder.Marker statement = builder.mark();
		builder.advanceLexer();
		checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.EXCEPT_KEYWORD) {
			while (builder.getTokenType() == org.consulo.python.PyTokenTypes.EXCEPT_KEYWORD) {
				PsiBuilder.Marker exceptBlock = builder.mark();
				builder.advanceLexer();
				if (builder.getTokenType() != org.consulo.python.PyTokenTypes.COLON) {
					if (!getExpressionParser().parseSingleExpression(builder, false)) {
						builder.error("expression expected");
					}
					if (builder.getTokenType() == org.consulo.python.PyTokenTypes.COMMA) {
						builder.advanceLexer();
						if (!getExpressionParser().parseSingleExpression(builder, true)) {
							builder.error("expression expected");
						}
					}
				}
				checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
				parseSuite(builder);
				exceptBlock.done(org.consulo.python.PyElementTypes.EXCEPT_BLOCK);
			}
			if (builder.getTokenType() == org.consulo.python.PyTokenTypes.ELSE_KEYWORD) {
				builder.advanceLexer();
				checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
				parseSuite(builder);
			}
			statement.done(org.consulo.python.PyElementTypes.TRY_EXCEPT_STATEMENT);
		} else if (builder.getTokenType() == org.consulo.python.PyTokenTypes.FINALLY_KEYWORD) {
			builder.advanceLexer();
			checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
			parseSuite(builder);
			statement.done(org.consulo.python.PyElementTypes.TRY_FINALLY_STATEMENT);
		} else {
			builder.error("'except' or 'finally' expected");

			statement.done(org.consulo.python.PyElementTypes.TRY_FINALLY_STATEMENT);
		}
	}

	private void parseClassDeclaration(PsiBuilder builder) {
		LOG.assertTrue(builder.getTokenType() == org.consulo.python.PyTokenTypes.CLASS_KEYWORD);
		PsiBuilder.Marker classMarker = builder.mark();
		builder.advanceLexer();
		checkMatches(builder, org.consulo.python.PyTokenTypes.IDENTIFIER, "identifier expected");
		PsiBuilder.Marker inheritMarker = builder.mark();
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.LPAR) {
			builder.advanceLexer();
			getExpressionParser().parseExpression(builder);
			checkMatches(builder, org.consulo.python.PyTokenTypes.RPAR, ") expected");
		}
		inheritMarker.done(org.consulo.python.PyElementTypes.PARENTHESIZED_EXPRESSION);
		checkMatches(builder, org.consulo.python.PyTokenTypes.COLON, "colon expected");
		parseSuite(builder);
		classMarker.done(org.consulo.python.PyElementTypes.CLASS_DECLARATION);
	}

	public void parseSuite(PsiBuilder builder) {
		parseSuite(builder, null, null);
	}

	public void parseSuite(PsiBuilder builder, PsiBuilder.Marker endMarker, IElementType elType) {
		if (builder.getTokenType() == org.consulo.python.PyTokenTypes.STATEMENT_BREAK) {
			builder.advanceLexer();

			PsiBuilder.Marker marker = builder.mark();
			if (builder.getTokenType() != org.consulo.python.PyTokenTypes.INDENT) {
				builder.error("indent expected");
			} else {
				builder.advanceLexer();
				while ((!builder.eof()) && (builder.getTokenType() != org.consulo.python.PyTokenTypes.DEDENT)) {
					parseStatement(builder);
				}
			}

			marker.done(org.consulo.python.PyElementTypes.STATEMENT_LIST);
			if (endMarker != null) {
				endMarker.done(elType);
			}
			if (!builder.eof()) {
				checkMatches(builder, org.consulo.python.PyTokenTypes.DEDENT, "dedent expected");
			}

			builder.getTokenType();
		} else {
			PsiBuilder.Marker marker = builder.mark();
			parseSimpleStatement(builder, true);
			while (builder.getTokenType() == org.consulo.python.PyTokenTypes.SEMICOLON) {
				builder.advanceLexer();
				parseSimpleStatement(builder, true);
			}
			marker.done(org.consulo.python.PyElementTypes.STATEMENT_LIST);
			if (endMarker != null)
				endMarker.done(elType);
		}
	}
}