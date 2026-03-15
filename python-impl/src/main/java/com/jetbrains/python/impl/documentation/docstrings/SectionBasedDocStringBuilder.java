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

import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public abstract class SectionBasedDocStringBuilder extends DocStringBuilder<SectionBasedDocStringBuilder>
{

	protected String mySectionIndent;
	protected final String myContinuationIndent;

	private String myCurSectionTitle = null;

	protected SectionBasedDocStringBuilder(String defaultSectionIndent, String defaultContinuationIndent)
	{
		mySectionIndent = defaultSectionIndent;
		myContinuationIndent = defaultContinuationIndent;
	}

	public SectionBasedDocStringBuilder startParametersSection()
	{
		// TODO make default section titles configurable
		return startSection(getDefaultParametersHeader());
	}

	public SectionBasedDocStringBuilder startReturnsSection()
	{
		return startSection(getDefaultReturnsHeader());
	}

	protected SectionBasedDocStringBuilder startSection(String title)
	{
		if(myCurSectionTitle != null)
		{
			addEmptyLine();
		}
		myCurSectionTitle = title;
		return this;
	}

	public SectionBasedDocStringBuilder endSection()
	{
		myCurSectionTitle = null;
		return this;
	}

	protected abstract String getDefaultParametersHeader();

	protected abstract String getDefaultReturnsHeader();

	public abstract SectionBasedDocStringBuilder addParameter(String name, @Nullable String type, String description);

	public abstract SectionBasedDocStringBuilder addReturnValue(@Nullable String name, String type, String description);

	protected SectionBasedDocStringBuilder addSectionLine(String line)
	{
		return addLine(mySectionIndent + line);
	}

	protected SectionBasedDocStringBuilder withSectionIndent(String indent)
	{
		mySectionIndent = indent;
		return this;
	}
}
