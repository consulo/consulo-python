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

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiParser;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.version.LanguageVersion;

/**
 * @author yole
 */
public class PyParser implements PsiParser
{
	private static final Logger LOGGER = Logger.getInstance(PyParser.class.getName());

	protected LanguageLevel myLanguageLevel;
	private StatementParsing.FUTURE myFutureFlag;

	public PyParser()
	{
		myLanguageLevel = LanguageLevel.getDefault();
	}

	public void setLanguageLevel(LanguageLevel languageLevel)
	{
		myLanguageLevel = languageLevel;
	}

	@Nonnull
	public ASTNode parse(IElementType root, PsiBuilder builder, LanguageVersion languageVersion)
	{
		long start = System.currentTimeMillis();
		final PsiBuilder.Marker rootMarker = builder.mark();
		ParsingContext context = createParsingContext(builder, myLanguageLevel, myFutureFlag);
		StatementParsing statementParser = context.getStatementParser();
		builder.setTokenTypeRemapper(statementParser); // must be done before touching the caching lexer with eof() call.
		boolean lastAfterSemicolon = false;
		while(!builder.eof())
		{
			context.pushScope(context.emptyParsingScope());
			if(lastAfterSemicolon)
			{
				statementParser.parseSimpleStatement();
			}
			else
			{
				statementParser.parseStatement();
			}
			lastAfterSemicolon = context.getScope().isAfterSemicolon();
			context.popScope();
		}
		rootMarker.done(root);
		ASTNode ast = builder.getTreeBuilt();
		long diff = System.currentTimeMillis() - start;
		double kb = builder.getCurrentOffset() / 1000.0;
		LOGGER.debug("Parsed " + String.format("%.1f", kb) + "K file in " + diff + "ms");
		return ast;
	}

	protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag)
	{
		return new ParsingContext(builder, languageLevel, futureFlag);
	}

	public void setFutureFlag(StatementParsing.FUTURE future)
	{
		myFutureFlag = future;
	}
}
