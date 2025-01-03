/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.console.parsing;

import jakarta.annotation.Nonnull;
import consulo.language.ast.ASTNode;
import consulo.language.parser.PsiBuilder;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.parsing.ParsingContext;
import com.jetbrains.python.impl.parsing.PyParser;
import com.jetbrains.python.impl.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.version.LanguageVersion;

/**
 * @author traff
 */
public class PyConsoleParser extends PyParser
{
	private StatementParsing.FUTURE myFutureFlag;
	private PythonConsoleData myPythonConsoleData;
	private boolean myIPythonStartSymbol;

	public PyConsoleParser(PythonConsoleData pythonConsoleData, LanguageLevel languageLevel)
	{
		myPythonConsoleData = pythonConsoleData;
		myLanguageLevel = languageLevel;
	}

	@Nonnull
	@Override
	public ASTNode parse(IElementType root, PsiBuilder builder, LanguageVersion languageVersion)
	{
		final PsiBuilder.Marker rootMarker = builder.mark();

		myIPythonStartSymbol = myPythonConsoleData.isIPythonEnabled() && startsWithIPythonSpecialSymbol(builder);

		ParsingContext context = createParsingContext(builder, myLanguageLevel, myFutureFlag);

		StatementParsing stmt_parser = context.getStatementParser();
		builder.setTokenTypeRemapper(stmt_parser); // must be done before touching the caching lexer with eof() call.

		while(!builder.eof())
		{
			stmt_parser.parseStatement();
		}
		rootMarker.done(root);
		return builder.getTreeBuilt();
	}

	public static boolean startsWithIPythonSpecialSymbol(PsiBuilder builder)
	{
		IElementType tokenType = builder.getTokenType();
		return builder.getTokenType() == PyConsoleTokenTypes.QUESTION_MARK || tokenType == PyTokenTypes.PERC || tokenType == PyTokenTypes.COMMA || tokenType == PyTokenTypes.SEMICOLON ||
				"/".equals(builder.getTokenText());
	}


	@Override
	protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag)
	{
		return new PyConsoleParsingContext(builder, languageLevel, futureFlag, myPythonConsoleData, myIPythonStartSymbol);
	}
}
