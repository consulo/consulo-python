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
package com.jetbrains.python.impl.documentation.docstrings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.google.common.collect.Maps;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;

/**
 * @author yole
 */
public abstract class TagBasedDocString extends DocStringLineParser implements StructuredDocString
{
	protected final String myDescription;

	protected final Map<String, Substring> mySimpleTagValues = Maps.newHashMap();
	protected final Map<String, Map<Substring, Substring>> myArgTagValues = Maps.newHashMap();

	private static final Pattern RE_STRICT_TAG_LINE = Pattern.compile("([a-z]+)(\\s+:class:[^:]*|[^:]*)\\s*:\\s*?(.*)");
	private static final Pattern RE_LOOSE_TAG_LINE = Pattern.compile("([a-z]+)\\s+([a-zA-Z_0-9]*)\\s*:?\\s*?([^:]*)");
	private static final Pattern RE_ARG_TYPE = Pattern.compile("(.*?)\\s+([a-zA-Z_0-9]+)");

	public static String[] PARAM_TAGS = new String[]{
			"param",
			"parameter",
			"arg",
			"argument"
	};
	public static String[] PARAM_TYPE_TAGS = new String[]{"type"};
	public static String[] VARIABLE_TAGS = new String[]{
			"ivar",
			"cvar",
			"var"
	};

	public static String[] RAISES_TAGS = new String[]{
			"raises",
			"raise",
			"except",
			"exception"
	};
	public static String[] RETURN_TAGS = new String[]{
			"return",
			"returns"
	};
	@Nonnull
	private final String myTagPrefix;

	public static String TYPE = "type";

	protected TagBasedDocString(@Nonnull Substring docStringText, @Nonnull String tagPrefix)
	{
		super(docStringText);
		myTagPrefix = tagPrefix;
		StringBuilder builder = new StringBuilder();
		int lineno = 0;
		while(lineno < getLineCount())
		{
			Substring line = getLine(lineno).trim();
			if(line.startsWith(tagPrefix))
			{
				lineno = parseTag(lineno, tagPrefix);
			}
			else
			{
				builder.append(line.toString()).append("\n");
			}
			lineno++;
		}
		myDescription = builder.toString();
	}

	public abstract List<String> getAdditionalTags();

	@Nonnull
	@Override
	public String getDescription()
	{
		return myDescription;
	}

	@Override
	public String getSummary()
	{
		List<String> strings = StringUtil.split(StringUtil.trimLeading(myDescription), "\n", true, false);
		if(strings.size() > 1)
		{
			if(strings.get(1).isEmpty())
			{
				return strings.get(0);
			}
		}
		return "";
	}

	@Nonnull
	private Map<Substring, Substring> getTagValuesMap(String key)
	{
		Map<Substring, Substring> map = myArgTagValues.get(key);
		if(map == null)
		{
			map = Maps.newLinkedHashMap();
			myArgTagValues.put(key, map);
		}
		return map;
	}

	protected int parseTag(int lineno, String tagPrefix)
	{
		Substring lineWithPrefix = getLine(lineno).trimLeft();
		if(lineWithPrefix.startsWith(tagPrefix))
		{
			Substring line = lineWithPrefix.substring(tagPrefix.length());
			Matcher strictTagMatcher = RE_STRICT_TAG_LINE.matcher(line);
			Matcher looseTagMatcher = RE_LOOSE_TAG_LINE.matcher(line);
			Matcher tagMatcher = null;
			if(strictTagMatcher.matches())
			{
				tagMatcher = strictTagMatcher;
			}
			else if(looseTagMatcher.matches())
			{
				tagMatcher = looseTagMatcher;
			}
			if(tagMatcher != null)
			{
				Substring tagName = line.getMatcherGroup(tagMatcher, 1);
				Substring argName = line.getMatcherGroup(tagMatcher, 2).trim();
				TextRange firstArgLineRange = line.getMatcherGroup(tagMatcher, 3).trim().getTextRange();
				int linesCount = getLineCount();
				int argStart = firstArgLineRange.getStartOffset();
				int argEnd = firstArgLineRange.getEndOffset();
				while(lineno + 1 < linesCount)
				{
					Substring nextLine = getLine(lineno + 1).trim();
					if(nextLine.isEmpty() || nextLine.startsWith(tagPrefix))
					{
						break;
					}
					argEnd = nextLine.getTextRange().getEndOffset();
					lineno++;
				}
				Substring argValue = new Substring(argName.getSuperString(), argStart, argEnd);
				String tagNameString = tagName.toString();
				if(argName.isEmpty())
				{
					mySimpleTagValues.put(tagNameString, argValue);
				}
				else
				{
					if("param".equals(tagNameString) || "parameter".equals(tagNameString) ||
							"arg".equals(tagNameString) || "argument".equals(tagNameString))
					{
						Matcher argTypeMatcher = RE_ARG_TYPE.matcher(argName);
						if(argTypeMatcher.matches())
						{
							Substring type = argName.getMatcherGroup(argTypeMatcher, 1).trim();
							Substring arg = argName.getMatcherGroup(argTypeMatcher, 2);
							getTagValuesMap(TYPE).put(arg, type);
						}
						else
						{
							getTagValuesMap(tagNameString).put(argName, argValue);
						}
					}
					else
					{
						getTagValuesMap(tagNameString).put(argName, argValue);
					}
				}
			}
		}
		return lineno;
	}

	protected static List<String> toUniqueStrings(List<?> objects)
	{
		List<String> result = new ArrayList<>(objects.size());
		for(Object o : objects)
		{
			String s = o.toString();
			if(!result.contains(s))
			{
				result.add(s);
			}
		}
		return result;
	}

	@Nullable
	public Substring getTagValue(String... tagNames)
	{
		for(String tagName : tagNames)
		{
			Substring value = mySimpleTagValues.get(tagName);
			if(value != null)
			{
				return value;
			}
		}
		return null;
	}

	@Nullable
	public Substring getTagValue(String tagName, @Nonnull String argName)
	{
		Map<Substring, Substring> argValues = myArgTagValues.get(tagName);
		return argValues != null ? argValues.get(new Substring(argName)) : null;
	}

	@Nullable
	public Substring getTagValue(String[] tagNames, @Nonnull String argName)
	{
		for(String tagName : tagNames)
		{
			Map<Substring, Substring> argValues = myArgTagValues.get(tagName);
			if(argValues != null)
			{
				return argValues.get(new Substring(argName));
			}
		}
		return null;
	}

	public List<Substring> getTagArguments(String... tagNames)
	{
		for(String tagName : tagNames)
		{
			Map<Substring, Substring> map = myArgTagValues.get(tagName);
			if(map != null)
			{
				return new ArrayList<>(map.keySet());
			}
		}
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public List<Substring> getParameterSubstrings()
	{
		List<Substring> results = new ArrayList<>();
		results.addAll(getTagArguments(PARAM_TAGS));
		results.addAll(getTagArguments(PARAM_TYPE_TAGS));
		return results;
	}

	@Override
	protected boolean isBlockEnd(int lineNum)
	{
		return getLine(lineNum).trimLeft().startsWith(myTagPrefix);
	}
}
