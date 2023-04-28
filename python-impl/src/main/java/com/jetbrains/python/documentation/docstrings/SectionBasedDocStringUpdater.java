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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.CharArrayUtil;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString.Section;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString.SectionField;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.toolbox.Substring;

/**
 * @author Mikhail Golubev
 */
public abstract class SectionBasedDocStringUpdater extends DocStringUpdater<SectionBasedDocString>
{
	private static final ImmutableList<String> CANONICAL_SECTION_ORDER = ImmutableList.of(SectionBasedDocString.PARAMETERS_SECTION, SectionBasedDocString.KEYWORD_ARGUMENTS_SECTION,
			SectionBasedDocString.OTHER_PARAMETERS_SECTION, SectionBasedDocString.YIELDS_SECTION, SectionBasedDocString.RETURNS_SECTION, SectionBasedDocString.RAISES_SECTION);

	private final List<AddParameter> myAddParameterRequests = new ArrayList<>();

	public SectionBasedDocStringUpdater(@Nonnull SectionBasedDocString docString, @Nonnull String minContentIndent)
	{
		super(docString, minContentIndent);
	}

	@Override
	public final void addParameter(@Nonnull String name, @Nullable String type)
	{
		// because any of requests to add new parameter can lead to creation of a new parameter section
		// it's not safe to process them independently
		myAddParameterRequests.add(new AddParameter(name, type));
	}

	@Override
	public final void addReturnValue(@Nullable String type)
	{
		if(StringUtil.isEmpty(type))
		{
			return;
		}
		final Substring typeSub = myOriginalDocString.getReturnTypeSubstring();
		if(typeSub != null)
		{
			replace(typeSub.getTextRange(), type);
			return;
		}
		final Section returnSection = findFirstReturnSection();
		if(returnSection != null)
		{
			final List<SectionField> fields = returnSection.getFields();
			if(!fields.isEmpty())
			{
				final SectionField firstField = fields.get(0);
				final String newLine = createReturnLine(type, getSectionIndent(returnSection), getFieldIndent(returnSection, firstField));
				insertBeforeLine(getFieldStartLine(firstField), newLine);
			}
			else
			{
				final String newLine = createReturnLine(type, getSectionIndent(returnSection), getExpectedFieldIndent());
				insertAfterLine(getSectionTitleLastLine(returnSection), newLine);
			}
		}
		else
		{
			final SectionBasedDocStringBuilder builder = createBuilder().withSectionIndent(getExpectedFieldIndent()).startReturnsSection().addReturnValue(null, type, "");
			insertNewSection(builder, SectionBasedDocString.RETURNS_SECTION);
		}
	}

	@Override
	public void removeParameter(@Nonnull final String name)
	{
		for(Section section : myOriginalDocString.getParameterSections())
		{
			final List<SectionField> sectionFields = section.getFields();
			for(SectionField field : sectionFields)
			{
				final Substring nameSub = ContainerUtil.find(field.getNamesAsSubstrings(), substring -> substring.toString().equals(name));
				if(nameSub != null)
				{
					if(field.getNamesAsSubstrings().size() == 1)
					{
						final int endLine = getFieldEndLine(field);
						if(sectionFields.size() == 1)
						{
							removeLinesAndSpacesAfter(getSectionStartLine(section), endLine + 1);
						}
						else
						{
							final int startLine = getFieldStartLine(field);
							if(ContainerUtil.getLastItem(sectionFields) == field)
							{
								removeLines(startLine, endLine + 1);
							}
							else
							{
								removeLinesAndSpacesAfter(startLine, endLine + 1);
							}
						}
					}
					else
					{
						final Substring wholeParamName = expandParamNameSubstring(nameSub);
						remove(wholeParamName.getStartOffset(), wholeParamName.getEndOffset());
					}
					break;
				}
			}
		}
	}

	@Nonnull
	private static Substring expandParamNameSubstring(@Nonnull Substring name)
	{
		final String superString = name.getSuperString();
		int startWithStars = name.getStartOffset();
		int prevNonWhitespace = skipSpacesBackward(superString, name.getStartOffset() - 1);
		if(prevNonWhitespace >= 0 && superString.charAt(prevNonWhitespace) == '*')
		{
			startWithStars = CharArrayUtil.shiftBackward(superString, prevNonWhitespace, "*") + 1;
			prevNonWhitespace = skipSpacesBackward(superString, startWithStars - 1);
		}
		if(prevNonWhitespace >= 0 && superString.charAt(prevNonWhitespace) == ',')
		{
			return new Substring(superString, prevNonWhitespace, name.getEndOffset());
		}
		// end offset is always exclusive
		final int nextNonWhitespace = skipSpacesForward(superString, name.getEndOffset());
		if(nextNonWhitespace < superString.length() && superString.charAt(nextNonWhitespace) == ',')
		{
			// if we remove parameter with trailing comma (i.e. first parameter) remove whitespaces after it as well
			return new Substring(superString, startWithStars, skipSpacesForward(superString, nextNonWhitespace + 1));
		}
		return name;
	}

	private static int skipSpacesForward(@Nonnull String superString, int offset)
	{
		return CharArrayUtil.shiftForward(superString, offset, " \t");
	}

	private static int skipSpacesBackward(@Nonnull String superString, int offset)
	{
		return CharArrayUtil.shiftBackward(superString, offset, " \t");
	}

	@Override
	protected void beforeApplyingModifications()
	{
		final List<AddParameter> newParams = new ArrayList<>();
		for(AddParameter param : myAddParameterRequests)
		{
			if(param.type != null)
			{
				final Substring typeSub = myOriginalDocString.getParamTypeSubstring(param.name);
				if(typeSub != null)
				{
					replace(typeSub.getTextRange(), param.type);
					continue;
				}
				final Substring nameSub = findParamNameSubstring(param.name);
				if(nameSub != null)
				{
					updateParamDeclarationWithType(nameSub, param.type);
					continue;
				}
			}
			newParams.add(param);
		}
		if(!newParams.isEmpty())
		{
			final SectionBasedDocStringBuilder paramBlockBuilder = createBuilder();
			final Section firstParamSection = findFirstParametersSection();
			// Insert whole new parameter block
			if(firstParamSection == null)
			{
				paramBlockBuilder.startParametersSection();
				final SectionBasedDocStringBuilder builder = addParametersInBlock(paramBlockBuilder, newParams, getExpectedFieldIndent());
				insertNewSection(builder, SectionBasedDocString.PARAMETERS_SECTION);
			}
			// Update existing parameter block
			else
			{
				final SectionField firstParamField = ContainerUtil.getFirstItem(firstParamSection.getFields());
				// Section exist, but empty
				if(firstParamField == null)
				{
					final String blockText = buildBlock(paramBlockBuilder, newParams, getExpectedFieldIndent(), getSectionIndent(firstParamSection));
					insertAfterLine(getSectionTitleLastLine(firstParamSection), blockText);
				}
				else
				{
					// Section contain other parameter declarations
					final String blockText = buildBlock(paramBlockBuilder, newParams, getFieldIndent(firstParamSection, firstParamField), getSectionIndent(firstParamSection));
					insertBeforeLine(getFieldStartLine(firstParamField), blockText);
				}
			}
		}
	}

	@Nonnull
	private static String buildBlock(@Nonnull SectionBasedDocStringBuilder builder, @Nonnull List<AddParameter> params, @Nonnull String sectionIndent, @Nonnull String indent)
	{
		return addParametersInBlock(builder, params, sectionIndent).buildContent(indent, true);
	}

	private static SectionBasedDocStringBuilder addParametersInBlock(@Nonnull SectionBasedDocStringBuilder builder, @Nonnull List<AddParameter> params, @Nonnull String sectionIndent)
	{
		builder.withSectionIndent(sectionIndent);
		for(AddParameter param : params)
		{
			builder.addParameter(param.name, param.type, "");
		}
		return builder;
	}

	private void insertNewSection(@Nonnull SectionBasedDocStringBuilder builder, @Nonnull String sectionTitle)
	{
		final Pair<Integer, Boolean> pos = findPreferredSectionLine(sectionTitle);
		if(pos.getSecond())
		{
			// don't add extra first empty line in empty docstring
			if(!myOriginalDocString.isEmpty(pos.getFirst()))
			{
				builder.addEmptyLine(0);
			}
			insertAfterLine(pos.getFirst(), builder.buildContent(getExpectedSectionIndent(), true));
		}
		else
		{
			if(!myOriginalDocString.isEmpty(pos.getFirst()))
			{
				builder.addEmptyLine();
			}
			insertBeforeLine(pos.getFirst(), builder.buildContent(getExpectedSectionIndent(), true));
		}
	}

	/**
	 * @return pair (lineNum, insertAfter), i.e. first item is line number,
	 * second item is true if new section should be inserted after this line and false otherwise
	 */
	private Pair<Integer, Boolean> findPreferredSectionLine(@Nonnull String sectionTitle)
	{
		final String normalized = SectionBasedDocString.getNormalizedSectionTitle(sectionTitle);
		final int index = CANONICAL_SECTION_ORDER.indexOf(normalized);
		if(index < 0)
		{
			return Pair.create(findLastNonEmptyLine(), true);
		}
		final Map<String, Section> namedSections = new HashMap<>();
		for(Section section : myOriginalDocString.getSections())
		{
			final String normalizedTitle = section.getNormalizedTitle();
			// leave only first occurrences
			if(!namedSections.containsKey(normalizedTitle))
			{
				namedSections.put(normalizedTitle, section);
			}
		}
		for(int i = index - 1; i >= 0; i--)
		{
			final Section previous = namedSections.get(CANONICAL_SECTION_ORDER.get(i));
			if(previous != null)
			{
				return Pair.create(getSectionEndLine(previous), true);
			}
		}
		for(int i = index + 1; i < CANONICAL_SECTION_ORDER.size(); i++)
		{
			final Section next = namedSections.get(CANONICAL_SECTION_ORDER.get(i));
			if(next != null)
			{
				return Pair.create(getSectionStartLine(next), false);
			}
		}
		return Pair.create(findLastNonEmptyLine(), true);
	}


	protected abstract void updateParamDeclarationWithType(@Nonnull Substring nameSubstring, @Nonnull String type);

	@Nonnull
	protected abstract SectionBasedDocStringBuilder createBuilder();

	@Nullable
	private Substring findParamNameSubstring(@Nonnull final String name)
	{
		return ContainerUtil.find(myOriginalDocString.getParameterSubstrings(), substring -> substring.toString().equals(name));
	}

	protected int getSectionTitleLastLine(@Nonnull Section paramSection)
	{
		return getSectionStartLine(paramSection);
	}

	protected String createReturnLine(@Nonnull String type, @Nonnull String docStringIndent, @Nonnull String sectionIndent)
	{
		return createBuilder().withSectionIndent(sectionIndent).addReturnValue(null, type, "").buildContent(docStringIndent, true);
	}

	@Nullable
	protected Section findFirstParametersSection()
	{
		return ContainerUtil.find(myOriginalDocString.getSections(), section -> section.getNormalizedTitle().equals(SectionBasedDocString.PARAMETERS_SECTION));
	}

	@Nullable
	protected Section findFirstReturnSection()
	{
		return ContainerUtil.find(myOriginalDocString.getSections(), section -> section.getNormalizedTitle().equals(SectionBasedDocString.RETURNS_SECTION));
	}

	@Nonnull
	protected String getExpectedSectionIndent()
	{
		final Section first = ContainerUtil.getFirstItem(myOriginalDocString.getSections());
		return first != null ? getSectionIndent(first) : myMinContentIndent;
	}

	@Nonnull
	protected String getExpectedFieldIndent()
	{
		for(Section section : myOriginalDocString.getSections())
		{
			final List<SectionField> fields = section.getFields();
			if(fields.isEmpty())
			{
				continue;
			}
			return getFieldIndent(section, fields.get(0));
		}
		return createBuilder().mySectionIndent;
	}

	@Nonnull
	protected String getFieldIndent(@Nonnull Section section, @Nonnull SectionField field)
	{
		final String titleIndent = getSectionIndent(section);
		final String fieldIndent = getLineIndent(getFieldStartLine(field));
		final int diffSize = Math.max(0, PyIndentUtil.getLineIndentSize(fieldIndent) - PyIndentUtil.getLineIndentSize(titleIndent));
		return StringUtil.repeatSymbol(' ', diffSize);
	}

	@Nonnull
	protected String getSectionIndent(@Nonnull Section section)
	{
		return getLineIndent(getSectionStartLine(section));
	}

	protected int getSectionStartLine(@Nonnull Section section)
	{
		return section.getTitleAsSubstring().getStartLine();
	}

	protected int getSectionEndLine(@Nonnull Section section)
	{
		final List<SectionField> fields = section.getFields();
		//noinspection ConstantConditions
		return fields.isEmpty() ? getSectionTitleLastLine(section) : getFieldEndLine(ContainerUtil.getLastItem(fields));
	}

	protected int getFieldStartLine(@Nonnull SectionField field)
	{
		return chooseFirstNotNull(field.getNameAsSubstring(), field.getTypeAsSubstring(), field.getDescriptionAsSubstring()).getStartLine();
	}

	protected int getFieldEndLine(@Nonnull SectionField field)
	{
		return chooseFirstNotNull(field.getDescriptionAsSubstring(), field.getTypeAsSubstring(), field.getNameAsSubstring()).getEndLine();
	}

	@Nonnull
	private static <T> T chooseFirstNotNull(@Nonnull T... values)
	{
		for(T value : values)
		{
			if(value != null)
			{
				return value;
			}
		}
		throw new NullPointerException("At least one of values must be not null");
	}

	private static class AddParameter
	{
		@Nonnull
		final String name;
		@Nullable
		final String type;

		public AddParameter(@Nonnull String name, @Nullable String type)
		{
			this.name = name;
			this.type = type;
		}
	}
}
