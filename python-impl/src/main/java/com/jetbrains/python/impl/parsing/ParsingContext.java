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
package com.jetbrains.python.impl.parsing;

import java.util.ArrayDeque;
import java.util.Deque;

import jakarta.annotation.Nonnull;
import consulo.language.parser.PsiBuilder;
import com.jetbrains.python.psi.LanguageLevel;

public class ParsingContext
{
	private final StatementParsing stmtParser;
	private final ExpressionParsing expressionParser;
	private final FunctionParsing functionParser;
	private final PsiBuilder myBuilder;
	private final LanguageLevel myLanguageLevel;
	private final Deque<ParsingScope> myScopes;

	public ParsingContext(final PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag)
	{
		myBuilder = builder;
		myLanguageLevel = languageLevel;
		stmtParser = new StatementParsing(this, futureFlag);
		expressionParser = new ExpressionParsing(this);
		functionParser = new FunctionParsing(this);
		myScopes = new ArrayDeque<>();
		myScopes.push(emptyParsingScope());
	}

	@Nonnull
	public ParsingScope popScope()
	{
		return myScopes.pop();
	}

	public void pushScope(@Nonnull ParsingScope scope)
	{
		myScopes.push(scope);
	}

	@Nonnull
	public ParsingScope getScope()
	{
		return myScopes.peek();
	}

	public StatementParsing getStatementParser()
	{
		return stmtParser;
	}

	public ExpressionParsing getExpressionParser()
	{
		return expressionParser;
	}

	public FunctionParsing getFunctionParser()
	{
		return functionParser;
	}

	public PsiBuilder getBuilder()
	{
		return myBuilder;
	}

	public LanguageLevel getLanguageLevel()
	{
		return myLanguageLevel;
	}

	public ParsingScope emptyParsingScope()
	{
		return new ParsingScope();
	}
}
