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

import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.psi.PyIndentUtil;

/**
 * @author Mikhail Golubev
 */
public class GoogleCodeStyleDocStringBuilder extends SectionBasedDocStringBuilder
{
	public static final String DEFAULT_CONTINUATION_INDENT = PyIndentUtil.FOUR_SPACES;

	@Nonnull
	public static String getDefaultSectionIndent(@Nonnull Project project)
	{
		return PyIndentUtil.getIndentFromSettings(project);
	}

	@Nonnull
	public static GoogleCodeStyleDocStringBuilder forProject(@Nonnull Project project)
	{
		return new GoogleCodeStyleDocStringBuilder(getDefaultSectionIndent(project));
	}

	public GoogleCodeStyleDocStringBuilder(@Nonnull String sectionIndent)
	{
		super(sectionIndent, DEFAULT_CONTINUATION_INDENT);
	}

	@Override
	@Nonnull
	protected String getDefaultParametersHeader()
	{
		return "Args";
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
		addLine(StringUtil.capitalize(title) + ":");
		return this;
	}

	@Nonnull
	@Override
	public SectionBasedDocStringBuilder addParameter(@Nonnull String name, @Nullable String type, @Nonnull String description)
	{
		if(type != null)
		{
			addSectionLine(String.format("%s (%s): %s", name, type, description));
		}
		else
		{
			addSectionLine(String.format("%s: %s", name, description));
		}
		return this;
	}

	@Nonnull
	@Override
	public SectionBasedDocStringBuilder addReturnValue(@Nullable String name, @Nonnull String type, @Nonnull String description)
	{
		if(name != null)
		{
			addSectionLine(String.format("%s (%s): %s", name, type, description));
		}
		else
		{
			addSectionLine(String.format("%s: %s", type, description));
		}
		return this;
	}
}
