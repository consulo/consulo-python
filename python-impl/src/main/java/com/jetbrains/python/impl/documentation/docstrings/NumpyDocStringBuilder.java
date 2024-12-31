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
package com.jetbrains.python.impl.documentation.docstrings;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.psi.PyIndentUtil;

/**
 * @author Mikhail Golubev
 */
public class NumpyDocStringBuilder extends SectionBasedDocStringBuilder
{
	public static final String DEFAULT_SECTION_INDENT = "";
	public static final String DEFAULT_CONTINUATION_INDENT = PyIndentUtil.FOUR_SPACES;
	public static final char DEFAULT_SECTION_TITLE_UNDERLINE_SYMBOL = '-';

	private char myUnderlineSymbol = DEFAULT_SECTION_TITLE_UNDERLINE_SYMBOL;

	public NumpyDocStringBuilder()
	{
		// Sections are not indented and continuation indent of 4 spaces like in Numpy sources
		super(DEFAULT_SECTION_INDENT, DEFAULT_CONTINUATION_INDENT);
	}

	@Override
	@Nonnull
	protected String getDefaultParametersHeader()
	{
		return "Parameters";
	}

	@Override
	@Nonnull
	protected String getDefaultReturnsHeader()
	{
		return "Returns";
	}

	@Nonnull
	@Override
	protected SectionBasedDocStringBuilder startSection(@Nonnull String title)
	{
		super.startSection(title);
		addLine(StringUtil.capitalize(title));
		addLine(StringUtil.repeatSymbol(myUnderlineSymbol, title.length()));
		return this;
	}

	@Nonnull
	@Override
	public SectionBasedDocStringBuilder addParameter(@Nonnull String name, @Nullable String type, @Nonnull String description)
	{
		if(type != null)
		{
			addSectionLine(String.format("%s : %s", name, type));
		}
		else
		{
			addSectionLine(name);
		}
		if(!description.isEmpty())
		{
			addSectionLine(myContinuationIndent + description);
		}
		return this;
	}

	@Nonnull
	@Override
	public SectionBasedDocStringBuilder addReturnValue(@Nullable String name, @Nonnull String type, @Nonnull String description)
	{
		if(name != null)
		{
			addSectionLine(String.format("%s : %s", name, type));
		}
		else
		{
			addSectionLine(type);
		}
		if(!description.isEmpty())
		{
			addSectionLine(myContinuationIndent + description);
		}
		return this;
	}
}
