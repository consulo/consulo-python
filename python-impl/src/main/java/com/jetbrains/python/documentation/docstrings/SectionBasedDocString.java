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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;

/**
 * Common base class for docstring styles supported by Napoleon Sphinx extension.
 *
 * @author Mikhail Golubev
 * @see <a href="http://sphinxcontrib-napoleon.readthedocs.org/en/latest/index.html">Napoleon</a>
 */
public abstract class SectionBasedDocString extends DocStringLineParser implements StructuredDocString
{

	/**
	 * Frequently used section types
	 */
	@NonNls
	public static final String RETURNS_SECTION = "returns";
	@NonNls
	public static final String RAISES_SECTION = "raises";
	@NonNls
	public static final String KEYWORD_ARGUMENTS_SECTION = "keyword arguments";
	@NonNls
	public static final String PARAMETERS_SECTION = "parameters";
	@NonNls
	public static final String ATTRIBUTES_SECTION = "attributes";
	@NonNls
	public static final String METHODS_SECTION = "methods";
	@NonNls
	public static final String OTHER_PARAMETERS_SECTION = "other parameters";
	@NonNls
	public static final String YIELDS_SECTION = "yields";

	private static final Pattern PLAIN_TEXT = Pattern.compile("\\w+(\\s+\\w+){2}"); // dumb heuristic - consecutive words

	protected static final Map<String, String> SECTION_ALIASES = ImmutableMap.<String, String>builder().put("arguments", PARAMETERS_SECTION).put("args", PARAMETERS_SECTION).put("parameters",
			PARAMETERS_SECTION).put("keyword args", KEYWORD_ARGUMENTS_SECTION).put("keyword arguments", KEYWORD_ARGUMENTS_SECTION).put("other parameters", OTHER_PARAMETERS_SECTION).put("attributes",
			ATTRIBUTES_SECTION).put("methods", METHODS_SECTION).put("note", "notes").put("notes", "notes").put("example", "examples").put("examples", "examples").put("return",
			RETURNS_SECTION).put("returns", RETURNS_SECTION).put("yield", YIELDS_SECTION).put("yields", "yields").put("raises", RAISES_SECTION).put("references", "references").put("see also",
			"see also").put("warning", "warnings").put("warns", "warnings").put("warnings", "warnings").build();
	private static final Pattern SPHINX_REFERENCE_RE = Pattern.compile("(:\\w+:\\S+:`.+?`|:\\S+:`.+?`|`.+?`)");

	public static Set<String> SECTION_NAMES = SECTION_ALIASES.keySet();
	private static final ImmutableSet<String> SECTIONS_WITH_NAME_AND_OPTIONAL_TYPE = ImmutableSet.of(ATTRIBUTES_SECTION, PARAMETERS_SECTION, KEYWORD_ARGUMENTS_SECTION, OTHER_PARAMETERS_SECTION);
	private static final ImmutableSet<String> SECTIONS_WITH_TYPE_AND_OPTIONAL_NAME = ImmutableSet.of(RETURNS_SECTION, YIELDS_SECTION);
	private static final ImmutableSet<String> SECTIONS_WITH_TYPE = ImmutableSet.of(RAISES_SECTION);
	private static final ImmutableSet<String> SECTIONS_WITH_NAME = ImmutableSet.of(METHODS_SECTION);

	@Nullable
	public static String getNormalizedSectionTitle(@Nonnull @NonNls String title)
	{
		return SECTION_ALIASES.get(title.toLowerCase());
	}

	public static boolean isValidSectionTitle(@Nonnull @NonNls String title)
	{
		return StringUtil.isCapitalized(title) && getNormalizedSectionTitle(title) != null;
	}

	private final Substring mySummary;
	private final List<Section> mySections = new ArrayList<>();
	private final List<Substring> myOtherContent = new ArrayList<>();

	protected SectionBasedDocString(@Nonnull Substring text)
	{
		super(text);
		List<Substring> summary = Collections.emptyList();
		int startLine = consumeEmptyLines(parseHeader(0));
		int lineNum = startLine;
		while(lineNum < getLineCount())
		{
			final Pair<Section, Integer> parsedSection = parseSection(lineNum);
			if(parsedSection.getFirst() != null)
			{
				mySections.add(parsedSection.getFirst());
				lineNum = parsedSection.getSecond();
			}
			else if(lineNum == startLine)
			{
				final Pair<List<Substring>, Integer> parsedSummary = parseSummary(lineNum);
				summary = parsedSummary.getFirst();
				lineNum = parsedSummary.getSecond();
			}
			else
			{
				myOtherContent.add(getLine(lineNum));
				lineNum++;
			}
			lineNum = consumeEmptyLines(lineNum);
		}
		//noinspection ConstantConditions
		mySummary = summary.isEmpty() ? null : summary.get(0).union(summary.get(summary.size() - 1)).trim();
	}

	@Nonnull
	private Pair<List<Substring>, Integer> parseSummary(int lineNum)
	{
		final List<Substring> result = new ArrayList<>();
		while(!(isEmptyOrDoesNotExist(lineNum) || isBlockEnd(lineNum)))
		{
			result.add(getLine(lineNum));
			lineNum++;
		}
		return Pair.create(result, lineNum);
	}

	/**
	 * Used to parse e.g. optional function signature at the beginning of NumPy-style docstring
	 *
	 * @return first line from which to star parsing remaining sections
	 */
	protected int parseHeader(int startLine)
	{
		return startLine;
	}

	@Nonnull
	protected Pair<Section, Integer> parseSection(int sectionStartLine)
	{
		final Pair<Substring, Integer> parsedHeader = parseSectionHeader(sectionStartLine);
		if(parsedHeader.getFirst() == null)
		{
			return Pair.create(null, sectionStartLine);
		}
		final String normalized = getNormalizedSectionTitle(parsedHeader.getFirst().toString());
		if(normalized == null)
		{
			return Pair.create(null, sectionStartLine);
		}
		final List<SectionField> fields = new ArrayList<>();
		final int sectionIndent = getLineIndentSize(sectionStartLine);
		int lineNum = consumeEmptyLines(parsedHeader.getSecond());
		while(!isSectionBreak(lineNum, sectionIndent))
		{
			if(!isEmpty(lineNum))
			{
				final Pair<SectionField, Integer> parsedField = parseSectionField(lineNum, normalized, sectionIndent);
				if(parsedField.getFirst() != null)
				{
					fields.add(parsedField.getFirst());
					lineNum = parsedField.getSecond();
					continue;
				}
				else
				{
					myOtherContent.add(getLine(lineNum));
				}
			}
			lineNum++;
		}
		return Pair.create(new Section(parsedHeader.getFirst(), fields), lineNum);
	}

	@Nonnull
	protected Pair<SectionField, Integer> parseSectionField(int lineNum, @Nonnull String normalizedSectionTitle, int sectionIndent)
	{
		if(SECTIONS_WITH_NAME_AND_OPTIONAL_TYPE.contains(normalizedSectionTitle))
		{
			return parseSectionField(lineNum, sectionIndent, true, false);
		}
		if(SECTIONS_WITH_TYPE_AND_OPTIONAL_NAME.contains(normalizedSectionTitle))
		{
			return parseSectionField(lineNum, sectionIndent, true, true);
		}
		if(SECTIONS_WITH_NAME.contains(normalizedSectionTitle))
		{
			return parseSectionField(lineNum, sectionIndent, false, false);
		}
		if(SECTIONS_WITH_TYPE.contains(normalizedSectionTitle))
		{
			return parseSectionField(lineNum, sectionIndent, false, true);
		}
		return parseGenericField(lineNum, sectionIndent);
	}

	protected abstract Pair<SectionField, Integer> parseSectionField(int lineNum, int sectionIndent, boolean mayHaveType, boolean preferType);

	@Nonnull
	protected Pair<SectionField, Integer> parseGenericField(int lineNum, int sectionIndent)
	{
		final Pair<List<Substring>, Integer> pair = parseIndentedBlock(lineNum, getSectionIndentationThreshold(sectionIndent));
		final Substring firstLine = ContainerUtil.getFirstItem(pair.getFirst());
		final Substring lastLine = ContainerUtil.getLastItem(pair.getFirst());
		if(firstLine != null && lastLine != null)
		{
			return Pair.create(new SectionField((Substring) null, null, firstLine.union(lastLine).trim()), pair.getSecond());
		}
		return Pair.create(null, pair.getSecond());
	}

	@Nonnull
	protected abstract Pair<Substring, Integer> parseSectionHeader(int lineNum);

	protected boolean isSectionStart(int lineNum)
	{
		final Pair<Substring, Integer> pair = parseSectionHeader(lineNum);
		return pair.getFirst() != null;
	}

	protected boolean isSectionBreak(int lineNum, int curSectionIndent)
	{
		return lineNum >= getLineCount() ||
				// note that field may have the same indent as its containing section
				(!isEmpty(lineNum) && getLineIndentSize(lineNum) <= getSectionIndentationThreshold(curSectionIndent)) ||
				isSectionStart(lineNum);
	}

	/**
	 * Consumes all lines that are indented more than {@code blockIndent} and don't contain start of a new section.
	 * Trailing empty lines (e.g. due to indentation of closing triple quotes) are omitted in result.
	 *
	 * @param blockIndent indentation threshold, block ends with a line that has greater indentation
	 */
	@Nonnull
	protected Pair<List<Substring>, Integer> parseIndentedBlock(int lineNum, int blockIndent)
	{
		final int blockEnd = consumeIndentedBlock(lineNum, blockIndent);
		return Pair.create(myLines.subList(lineNum, blockEnd), blockEnd);
	}

	/**
	 * Inside section any indentation that is equal or smaller to returned one signals about section break.
	 * It's safe to return negative value, because it's used only for comparisons.
	 *
	 * @see #isSectionBreak(int, int)
	 * @see #parseGenericField(int, int)
	 */
	protected int getSectionIndentationThreshold(int sectionIndent)
	{
		return sectionIndent;
	}

	@Override
	protected boolean isBlockEnd(int lineNum)
	{
		return isSectionStart(lineNum);
	}

	protected boolean isValidType(@Nonnull String type)
	{
		return !type.isEmpty() && !PLAIN_TEXT.matcher(type).find();
	}

	protected boolean isValidName(@Nonnull String name)
	{
		return PyNames.isIdentifierString(name);
	}

	/**
	 * Properly partitions line by first colon taking into account possible Sphinx references inside
	 * <p>
	 * <h3>Example</h3>
	 * <pre><code>
	 *   runtime (:class:`Runtime`): Use it to access the environment.
	 * </code></pre>
	 */
	@Nonnull
	protected static List<Substring> splitByFirstColon(@Nonnull Substring line)
	{
		final List<Substring> parts = line.split(SPHINX_REFERENCE_RE);
		if(parts.size() > 1)
		{
			for(Substring part : parts)
			{
				final int i = part.indexOf(":");
				if(i >= 0)
				{
					final Substring beforeColon = new Substring(line.getSuperString(), line.getStartOffset(), part.getStartOffset() + i);
					final Substring afterColon = new Substring(line.getSuperString(), part.getStartOffset() + i + 1, line.getEndOffset());
					return Arrays.asList(beforeColon, afterColon);
				}
			}
			return Collections.singletonList(line);
		}
		return line.split(":", 1);
	}

	@Nonnull
	public List<Section> getSections()
	{
		return Collections.unmodifiableList(mySections);
	}

	@Override
	public String getSummary()
	{
		return mySummary != null ? mySummary.concatTrimmedLines("\n") : "";
	}

	@Nonnull
	@Override
	public String getDescription()
	{
		return "";
	}

	@Nonnull
	@Override
	public List<String> getParameters()
	{
		return ContainerUtil.map(getParameterSubstrings(), substring -> substring.toString());
	}

	@Nonnull
	@Override
	public List<Substring> getParameterSubstrings()
	{
		final List<Substring> result = new ArrayList<>();
		for(SectionField field : getParameterFields())
		{
			ContainerUtil.addAllNotNull(result, field.getNamesAsSubstrings());
		}
		return result;
	}

	@Nullable
	@Override
	public String getParamType(@Nullable String paramName)
	{
		final Substring sub = getParamTypeSubstring(paramName);
		return sub != null ? sub.toString() : null;
	}

	@Nullable
	@Override
	public Substring getParamTypeSubstring(@Nullable String paramName)
	{
		if(paramName != null)
		{
			final SectionField field = getFirstFieldForParameter(paramName);
			if(field != null)
			{
				return field.getTypeAsSubstring();
			}
		}
		return null;
	}

	@Nullable
	@Override
	public String getParamDescription(@Nullable String paramName)
	{
		if(paramName != null)
		{
			final SectionField field = getFirstFieldForParameter(paramName);
			if(field != null)
			{
				return field.getDescription();
			}
		}
		return null;
	}

	@Nullable
	public SectionField getFirstFieldForParameter(@Nonnull final String name)
	{
		return ContainerUtil.find(getParameterFields(), field -> field.getNames().contains(name));
	}

	@Nonnull
	public List<SectionField> getParameterFields()
	{
		final List<SectionField> result = new ArrayList<>();
		for(Section section : getParameterSections())
		{
			result.addAll(section.getFields());
		}
		return result;
	}

	@Nonnull
	public List<Section> getParameterSections()
	{
		return getSectionsWithNormalizedTitle(PARAMETERS_SECTION);
	}

	@Nonnull
	@Override
	public List<String> getKeywordArguments()
	{
		final List<String> result = new ArrayList<>();
		for(SectionField field : getKeywordArgumentFields())
		{
			result.addAll(field.getNames());
		}
		return result;
	}

	@Nonnull
	@Override
	public List<Substring> getKeywordArgumentSubstrings()
	{
		final List<Substring> result = new ArrayList<>();
		for(SectionField field : getKeywordArgumentFields())
		{
			ContainerUtil.addAllNotNull(field.getNamesAsSubstrings());
		}
		return result;
	}

	@Nullable
	@Override
	public String getKeywordArgumentDescription(@Nullable String paramName)
	{
		if(paramName != null)
		{
			final SectionField argument = getFirstFieldForKeywordArgument(paramName);
			if(argument != null)
			{
				return argument.getDescription();
			}
		}
		return null;
	}

	@Nonnull
	public List<SectionField> getKeywordArgumentFields()
	{
		final List<SectionField> result = new ArrayList<>();
		for(Section section : getSectionsWithNormalizedTitle(KEYWORD_ARGUMENTS_SECTION))
		{
			result.addAll(section.getFields());
		}
		return result;
	}

	@Nullable
	private SectionField getFirstFieldForKeywordArgument(@Nonnull final String name)
	{
		return ContainerUtil.find(getKeywordArgumentFields(), field -> field.getNames().contains(name));
	}

	@Nullable
	@Override
	public String getReturnType()
	{
		final Substring sub = getReturnTypeSubstring();
		return sub != null ? sub.toString() : null;
	}

	@Nullable
	@Override
	public Substring getReturnTypeSubstring()
	{
		final SectionField field = getFirstReturnField();
		return field != null ? field.getTypeAsSubstring() : null;
	}

	@Nullable
	@Override
	public String getReturnDescription()
	{
		final SectionField field = getFirstReturnField();
		return field != null ? field.getDescription() : null;
	}


	@Nonnull
	public List<SectionField> getReturnFields()
	{
		final List<SectionField> result = new ArrayList<>();
		for(Section section : getSectionsWithNormalizedTitle(RETURNS_SECTION))
		{
			result.addAll(section.getFields());
		}
		return result;
	}

	@Nullable
	private SectionField getFirstReturnField()
	{
		return ContainerUtil.getFirstItem(getReturnFields());
	}

	@Nonnull
	@Override
	public List<String> getRaisedExceptions()
	{
		return ContainerUtil.mapNotNull(getExceptionFields(), field -> StringUtil.nullize(field.getType()));
	}

	@Nullable
	@Override
	public String getRaisedExceptionDescription(@Nullable String exceptionName)
	{
		if(exceptionName != null)
		{
			final SectionField exception = getFirstFieldForException(exceptionName);
			if(exception != null)
			{
				return exception.getDescription();
			}
		}
		return null;
	}

	@Nonnull
	public List<SectionField> getExceptionFields()
	{
		final List<SectionField> result = new ArrayList<>();
		for(Section section : getSectionsWithNormalizedTitle(RAISES_SECTION))
		{
			result.addAll(section.getFields());
		}
		return result;
	}

	@Nullable
	private SectionField getFirstFieldForException(@Nonnull final String exceptionType)
	{
		return ContainerUtil.find(getExceptionFields(), field -> exceptionType.equals(field.getType()));
	}

	@Nonnull
	public List<SectionField> getAttributeFields()
	{
		final List<SectionField> result = new ArrayList<>();
		for(Section section : getSectionsWithNormalizedTitle(ATTRIBUTES_SECTION))
		{
			result.addAll(section.getFields());
		}
		return result;
	}

	@Nonnull
	public List<Section> getSectionsWithNormalizedTitle(@Nonnull final String title)
	{
		return ContainerUtil.mapNotNull(mySections, section -> section.getNormalizedTitle().equals(getNormalizedSectionTitle(title)) ? section : null);
	}

	@Nullable
	public Section getFirstSectionWithNormalizedTitle(@Nonnull String title)
	{
		return ContainerUtil.getFirstItem(getSectionsWithNormalizedTitle(title));
	}

	@Nullable
	@Override
	public String getAttributeDescription()
	{
		return null;
	}

	@Nonnull
	protected static Substring cleanUpName(@Nonnull Substring name)
	{
		int firstNotStar = 0;
		while(firstNotStar < name.length() && name.charAt(firstNotStar) == '*')
		{
			firstNotStar++;
		}
		return name.substring(firstNotStar).trimLeft();
	}

	public static class Section
	{
		private final Substring myTitle;
		private final List<SectionField> myFields;

		public Section(@Nonnull Substring title, @Nonnull List<SectionField> fields)
		{
			myTitle = title;
			myFields = new ArrayList<>(fields);
		}

		@Nonnull
		public Substring getTitleAsSubstring()
		{
			return myTitle;
		}

		@Nonnull
		public String getTitle()
		{
			return myTitle.toString();
		}

		@Nonnull
		public String getNormalizedTitle()
		{
			//noinspection ConstantConditions
			return getNormalizedSectionTitle(getTitle());
		}

		@Nonnull
		public List<SectionField> getFields()
		{
			return Collections.unmodifiableList(myFields);
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}

			Section section = (Section) o;

			if(!myTitle.equals(section.myTitle))
			{
				return false;
			}
			if(!myFields.equals(section.myFields))
			{
				return false;
			}

			return true;
		}

		@Override
		public int hashCode()
		{
			int result = myTitle.hashCode();
			result = 31 * result + myFields.hashCode();
			return result;
		}
	}

	public static class SectionField
	{
		private final List<Substring> myNames;
		private final Substring myType;
		private final Substring myDescription;

		public SectionField(@Nullable Substring name, @Nullable Substring type, @Nullable Substring description)
		{
			this(name == null ? Collections.<Substring>emptyList() : Collections.singletonList(name), type, description);
		}

		public SectionField(@Nonnull List<Substring> names, @Nullable Substring type, @Nullable Substring description)
		{
			myNames = names;
			myType = type;
			myDescription = description;
		}

		@Nullable
		public String getName()
		{
			return myNames.isEmpty() ? null : myNames.get(0).toString();
		}

		@Nullable
		public Substring getNameAsSubstring()
		{
			return myNames.isEmpty() ? null : myNames.get(0);
		}

		@Nonnull
		public List<Substring> getNamesAsSubstrings()
		{
			return myNames;
		}

		@Nonnull
		public List<String> getNames()
		{
			return ContainerUtil.map(myNames, substring -> substring.toString());
		}

		@Nullable
		public String getType()
		{
			return myType == null ? null : myType.toString();
		}

		@Nullable
		public Substring getTypeAsSubstring()
		{
			return myType;
		}

		@Nullable
		public String getDescription()
		{
			return myDescription == null ? null : PyIndentUtil.removeCommonIndent(myDescription.getValue(), true);
		}

		@Nullable
		public Substring getDescriptionAsSubstring()
		{
			return myDescription;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}

			SectionField field = (SectionField) o;

			if(myNames != null ? !myNames.equals(field.myNames) : field.myNames != null)
			{
				return false;
			}
			if(myType != null ? !myType.equals(field.myType) : field.myType != null)
			{
				return false;
			}
			if(myDescription != null ? !myDescription.equals(field.myDescription) : field.myDescription != null)
			{
				return false;
			}

			return true;
		}

		@Override
		public int hashCode()
		{
			int result = myNames != null ? myNames.hashCode() : 0;
			result = 31 * result + (myType != null ? myType.hashCode() : 0);
			result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
			return result;
		}
	}
}
