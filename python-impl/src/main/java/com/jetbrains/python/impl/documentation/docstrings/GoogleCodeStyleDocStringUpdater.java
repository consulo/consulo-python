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

import consulo.project.Project;
import com.jetbrains.python.toolbox.Substring;

/**
 * @author Mikhail Golubev
 */
public class GoogleCodeStyleDocStringUpdater extends SectionBasedDocStringUpdater
{
	private final String myFallbackSectionIndent;

	public static GoogleCodeStyleDocStringUpdater forProject(@Nonnull GoogleCodeStyleDocString docString, @Nonnull String minContentIndent, @Nonnull Project project)
	{
		return new GoogleCodeStyleDocStringUpdater(docString, minContentIndent, GoogleCodeStyleDocStringBuilder.getDefaultSectionIndent(project));
	}

	public GoogleCodeStyleDocStringUpdater(@Nonnull GoogleCodeStyleDocString docString, @Nonnull String minContentIndent, @Nonnull String fallbackSectionIndent)
	{
		super(docString, minContentIndent);
		myFallbackSectionIndent = fallbackSectionIndent;
	}

	@Override
	protected void updateParamDeclarationWithType(@Nonnull Substring nameSubstring, @Nonnull String type)
	{
		insert(nameSubstring.getEndOffset(), " (" + type + ")");
	}

	@Nonnull
	@Override
	protected SectionBasedDocStringBuilder createBuilder()
	{
		return new GoogleCodeStyleDocStringBuilder(myFallbackSectionIndent);
	}
}
