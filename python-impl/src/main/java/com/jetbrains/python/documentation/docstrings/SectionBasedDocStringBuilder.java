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
package com.jetbrains.python.documentation.docstrings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Mikhail Golubev
 */
public abstract class SectionBasedDocStringBuilder extends DocStringBuilder<SectionBasedDocStringBuilder>
{

	protected String mySectionIndent;
	protected final String myContinuationIndent;

	private String myCurSectionTitle = null;

	protected SectionBasedDocStringBuilder(@Nonnull String defaultSectionIndent, @Nonnull String defaultContinuationIndent)
	{
		mySectionIndent = defaultSectionIndent;
		myContinuationIndent = defaultContinuationIndent;
	}

	@Nonnull
	public SectionBasedDocStringBuilder startParametersSection()
	{
		// TODO make default section titles configurable
		return startSection(getDefaultParametersHeader());
	}

	@Nonnull
	public SectionBasedDocStringBuilder startReturnsSection()
	{
		return startSection(getDefaultReturnsHeader());
	}

	@Nonnull
	protected SectionBasedDocStringBuilder startSection(@Nonnull String title)
	{
		if(myCurSectionTitle != null)
		{
			addEmptyLine();
		}
		myCurSectionTitle = title;
		return this;
	}

	@Nonnull
	public SectionBasedDocStringBuilder endSection()
	{
		myCurSectionTitle = null;
		return this;
	}

	@Nonnull
	protected abstract String getDefaultParametersHeader();

	@Nonnull
	protected abstract String getDefaultReturnsHeader();

	@Nonnull
	public abstract SectionBasedDocStringBuilder addParameter(@Nonnull String name, @Nullable String type, @Nonnull String description);

	@Nonnull
	public abstract SectionBasedDocStringBuilder addReturnValue(@Nullable String name, @Nonnull String type, @Nonnull String description);

	@Nonnull
	protected SectionBasedDocStringBuilder addSectionLine(@Nonnull String line)
	{
		return addLine(mySectionIndent + line);
	}

	@Nonnull
	protected SectionBasedDocStringBuilder withSectionIndent(@Nonnull String indent)
	{
		mySectionIndent = indent;
		return this;
	}
}
