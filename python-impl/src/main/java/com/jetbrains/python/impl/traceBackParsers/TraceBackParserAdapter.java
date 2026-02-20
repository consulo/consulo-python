/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.traceBackParsers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Adapter that uses regexps
 *
 * @author Ilya.Kazakevich
 */
public abstract class TraceBackParserAdapter implements TraceBackParser
{
	/**
	 * We do not search anything in line longer than this, because it makes no sense to search something in so long lines
	 */
	private static final int MAX_LINE_TO_PARSE = 5000;
	@Nonnull
	private final Pattern myPattern;

	/**
	 * @param pattern pattern to be used to match line.
	 */
	protected TraceBackParserAdapter(@Nonnull Pattern pattern)
	{
		myPattern = pattern;
	}


	@Nullable
	@Override
	public final LinkInTrace findLinkInTrace(@Nonnull String line)
	{
		if(line.length() > MAX_LINE_TO_PARSE)
		{
			// Cut down line is too long to parse (to prevent freeze)
			//noinspection AssignmentToMethodParameter
			line = line.substring(0, MAX_LINE_TO_PARSE);
		}
		Matcher matcher = myPattern.matcher(line);
		if(!matcher.find())
		{
			return null;
		}
		return findLinkInTrace(line, matcher);
	}


	/**
	 * Fetches link from line
	 *
	 * @param line           line to search link in
	 * @param matchedMatcher regex matcher that found link
	 * @return line info
	 */
	@Nonnull
	protected abstract LinkInTrace findLinkInTrace(@Nonnull String line, @Nonnull Matcher matchedMatcher);
}
