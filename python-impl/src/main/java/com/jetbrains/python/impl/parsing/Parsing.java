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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.parser.PsiBuilder;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;

/**
 * @author yole
 */
public class Parsing
{
	protected ParsingContext myContext;
	protected PsiBuilder myBuilder;
	private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.parsing.Parsing");

	protected Parsing(ParsingContext context)
	{
		myContext = context;
		myBuilder = context.getBuilder();
	}

	public ParsingContext getParsingContext()
	{
		return myContext;
	}

	public ExpressionParsing getExpressionParser()
	{
		return getParsingContext().getExpressionParser();
	}

	public StatementParsing getStatementParser()
	{
		return getParsingContext().getStatementParser();
	}

	public FunctionParsing getFunctionParser()
	{
		return getParsingContext().getFunctionParser();
	}

	protected boolean checkMatches(final IElementType token, final String message)
	{
		if(myBuilder.getTokenType() == token)
		{
			myBuilder.advanceLexer();
			return true;
		}
		myBuilder.error(message);
		return false;
	}

	protected boolean parseIdentifierOrSkip(@Nonnull IElementType... validSuccessiveTokens)
	{
		if(myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER)
		{
			myBuilder.advanceLexer();
			return true;
		}
		else
		{
			final PsiBuilder.Marker nameExpected = myBuilder.mark();
			if(myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK && !atAnyOfTokens(validSuccessiveTokens))
			{
				myBuilder.advanceLexer();
			}
			nameExpected.error(PyBundle.message("PARSE.expected.identifier"));
			return false;
		}
	}

	protected void assertCurrentToken(final PyElementType tokenType)
	{
		LOG.assertTrue(myBuilder.getTokenType() == tokenType);
	}

	protected boolean atToken(@Nullable final IElementType tokenType)
	{
		return myBuilder.getTokenType() == tokenType;
	}

	protected boolean atToken(@Nonnull final IElementType tokenType, @Nonnull String tokenText)
	{
		return myBuilder.getTokenType() == tokenType && tokenText.equals(myBuilder.getTokenText());
	}

	protected boolean atAnyOfTokens(final IElementType... tokenTypes)
	{
		IElementType currentTokenType = myBuilder.getTokenType();
		for(IElementType tokenType : tokenTypes)
		{
			if(currentTokenType == tokenType)
			{
				return true;
			}
		}
		return false;
	}

	protected boolean matchToken(final IElementType tokenType)
	{
		if(myBuilder.getTokenType() == tokenType)
		{
			myBuilder.advanceLexer();
			return true;
		}
		return false;
	}

	protected void nextToken()
	{
		myBuilder.advanceLexer();
	}

	protected static void buildTokenElement(IElementType type, PsiBuilder builder)
	{
		final PsiBuilder.Marker marker = builder.mark();
		builder.advanceLexer();
		marker.done(type);
	}

	protected IElementType getReferenceType()
	{
		return PyElementTypes.REFERENCE_EXPRESSION;
	}
}
