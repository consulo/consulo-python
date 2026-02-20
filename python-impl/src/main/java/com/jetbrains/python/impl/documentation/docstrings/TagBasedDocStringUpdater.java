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

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.jetbrains.python.toolbox.Substring;

/**
 * @author Mikhail Golubev
 */
public class TagBasedDocStringUpdater extends DocStringUpdater<TagBasedDocString>
{

	private final String myTagPrefix;

	public TagBasedDocStringUpdater(@Nonnull TagBasedDocString docString, @Nonnull String tagPrefix, @Nonnull String minContentIndent)
	{
		super(docString, minContentIndent);
		myTagPrefix = tagPrefix;
	}

	@Nonnull
	private TagBasedDocStringBuilder createBuilder()
	{
		return new TagBasedDocStringBuilder(myTagPrefix);
	}

	@Override
	public final void addParameter(@Nonnull String name, @Nullable String type)
	{
		if(type != null)
		{
			insertTagLine(createBuilder().addParameterType(name, type));
		}
		else
		{
			insertTagLine(createBuilder().addParameterDescription(name, ""));
		}
	}

	@Override
	public final void addReturnValue(@Nullable String type)
	{
		if(type != null)
		{
			insertTagLine(createBuilder().addReturnValueType(type));
		}
		else
		{
			insertTagLine(createBuilder().addReturnValueDescription(""));
		}
	}

	@Override
	public void removeParameter(@Nonnull String name)
	{
		List<Substring> nameSubs = myOriginalDocString.getParameterSubstrings();
		for(Substring sub : nameSubs)
		{
			if(sub.toString().equals(name))
			{
				int startLine = sub.getStartLine();
				int nextAfterBlock = myOriginalDocString.consumeIndentedBlock(startLine + 1, getLineIndentSize(startLine));
				removeLinesAndSpacesAfter(startLine, nextAfterBlock);
			}
		}
	}

	private void insertTagLine(@Nonnull DocStringBuilder lineBuilder)
	{
		int firstLineWithTag = findFirstLineWithTag();
		if(firstLineWithTag >= 0)
		{
			String indent = getLineIndent(firstLineWithTag);
			insertBeforeLine(firstLineWithTag, lineBuilder.buildContent(indent, true));
			return;
		}
		int lastNonEmptyLine = findLastNonEmptyLine();
		String indent = getLineIndent(lastNonEmptyLine);
		insertAfterLine(lastNonEmptyLine, lineBuilder.buildContent(indent, true));
	}

	private int findFirstLineWithTag()
	{
		for(int i = 0; i < myOriginalDocString.getLineCount(); i++)
		{
			Substring line = myOriginalDocString.getLine(i);
			if(line.trimLeft().startsWith(myTagPrefix))
			{
				return i;
			}
		}
		return -1;
	}
}
