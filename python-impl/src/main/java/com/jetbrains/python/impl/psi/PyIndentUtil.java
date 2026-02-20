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
package com.jetbrains.python.impl.psi;

import com.google.common.collect.Iterables;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyStatementList;
import consulo.application.util.LineTokenizer;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * Contains various methods for manipulation on indentation found in arbitrary text and individual lines:
 * <ul>
 * <li>calculating actual and expected indentation</li>
 * <li>finding common indentation of several lines</li>
 * <li>replacing and removing indentation of multiple lines</li>
 * </ul>
 * <p>
 * It indented to be used primarily when one needs to modify content of Python files on document level and preserve valid block structure.
 * Note that in most scenarios accurate indentation consistent with the code style settings is provided by automatic formatting pass
 * that is performed each time you modify PSI tree directly.
 *
 * @author Mikhail Golubev
 */
public class PyIndentUtil
{
	@NonNls
	public static final String TWO_SPACES = "  ";
	@NonNls
	public static final String FOUR_SPACES = "    ";

	private PyIndentUtil()
	{
	}

	/**
	 * Returns indentation size as number of characters <tt>' '</tt> and <tt>'\t'</tt> in the beginning of a line.
	 * It doesn't perform any expansion of tabs.
	 */
	public static int getLineIndentSize(@Nonnull CharSequence line)
	{
		int stop;
		for(stop = 0; stop < line.length(); stop++)
		{
			char c = line.charAt(stop);
			if(!(c == ' ' || c == '\t'))
			{
				break;
			}
		}
		return stop;
	}

	@Nonnull
	public static String getLineIndent(@Nonnull String line)
	{
		return line.substring(0, getLineIndentSize(line));
	}

	/**
	 * Useful version of {@link #getLineIndent(String)} for custom character sequences like {@link com.jetbrains.python.toolbox.Substring}.
	 */
	@Nonnull
	public static CharSequence getLineIndent(@Nonnull CharSequence line)
	{
		return line.subSequence(0, getLineIndentSize(line));
	}

	@Nonnull
	public static String getElementIndent(@Nonnull PsiElement anchor)
	{
		if(anchor instanceof PsiFile)
		{
			return "";
		}
		PyStatementList statementList = getAnchorStatementList(anchor);
		if(statementList == null)
		{
			return "";
		}
		PsiElement prevSibling = statementList.getPrevSibling();
		String whitespace = prevSibling instanceof PsiWhiteSpace ? prevSibling.getText() : "";
		int i = whitespace.lastIndexOf("\n");
		if(i >= 0 && statementList.getStatements().length != 0)
		{
			return whitespace.substring(i + 1);
		}
		else
		{
			return getExpectedBlockIndent(statementList);
		}
	}

	@Nonnull
	private static String getExpectedBlockIndent(@Nonnull PyStatementList anchor)
	{
		String indentStep = getIndentFromSettings(anchor.getProject());
		PyStatementList parentBlock = PsiTreeUtil.getParentOfType(anchor, PyStatementList.class, true);
		if(parentBlock != null)
		{
			return getElementIndent(parentBlock) + indentStep;
		}
		return indentStep;
	}

	@Nullable
	private static PyStatementList getAnchorStatementList(@Nonnull PsiElement element)
	{
		PyStatementList statementList = null;
		// First whitespace right before the statement list (right after ":")
		if(element instanceof PsiWhiteSpace)
		{
			statementList = as(element.getNextSibling(), PyStatementList.class);
		}
		if(statementList == null)
		{
			statementList = PsiTreeUtil.getParentOfType(element, PyStatementList.class, false);
		}
		return statementList;
	}

	private static int getExpectedElementIndentSize(@Nonnull PsiElement anchor)
	{
		int depth = 0;
		PyStatementList block = getAnchorStatementList(anchor);
		while(block != null)
		{
			depth += 1;
			block = PsiTreeUtil.getParentOfType(block, PyStatementList.class);
		}
		return depth * getIndentSizeFromSettings(anchor.getProject());
	}

	public static boolean areTabsUsedForIndentation(@Nonnull Project project)
	{
		CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
		return codeStyleSettings.useTabCharacter(PythonFileType.INSTANCE);
	}

	public static char getIndentCharacter(@Nonnull Project project)
	{
		return areTabsUsedForIndentation(project) ? '\t' : ' ';
	}

	/**
	 * Returns indentation size configured in the Python code style settings.
	 *
	 * @see #getIndentFromSettings(Project)
	 */
	public static int getIndentSizeFromSettings(@Nonnull Project project)
	{
		CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
		CodeStyleSettings.IndentOptions indentOptions = codeStyleSettings.getIndentOptions(PythonFileType.INSTANCE);
		return indentOptions.INDENT_SIZE;
	}

	/**
	 * Returns indentation configured in the Python code style settings either as space character repeated number times specified there
	 * or a single tab character if tabs are set to use for indentation.
	 *
	 * @see #getIndentSizeFromSettings(Project)
	 * @see #areTabsUsedForIndentation(Project)
	 */
	@Nonnull
	public static String getIndentFromSettings(@Nonnull Project project)
	{
		boolean useTabs = areTabsUsedForIndentation(project);
		return useTabs ? "\t" : StringUtil.repeatSymbol(' ', getIndentSizeFromSettings(project));
	}

	@Nonnull
	public static List<String> removeCommonIndent(@Nonnull Iterable<String> lines, boolean ignoreFirstLine)
	{
		return changeIndent(lines, ignoreFirstLine, "");
	}

	@Nonnull
	public static String removeCommonIndent(@Nonnull String s, boolean ignoreFirstLine)
	{
		List<String> trimmed = removeCommonIndent(LineTokenizer.tokenizeIntoList(s, false, false), ignoreFirstLine);
		return StringUtil.join(trimmed, "\n");
	}

	@Nonnull
	public static String changeIndent(@Nonnull String s, boolean ignoreFirstLine, String newIndent)
	{
		List<String> trimmed = changeIndent(LineTokenizer.tokenizeIntoList(s, false, false), ignoreFirstLine, newIndent);
		return StringUtil.join(trimmed, "\n");
	}

	/**
	 * Note that all empty lines will be trimmed regardless of their actual indentation.
	 */
	@Nonnull
	public static List<String> changeIndent(@Nonnull Iterable<String> lines, boolean ignoreFirstLine, String newIndent)
	{
		String oldIndent = findCommonIndent(lines, ignoreFirstLine);
		if(Iterables.isEmpty(lines))
		{
			return Collections.emptyList();
		}

		List<String> result = ContainerUtil.map(Iterables.skip(lines, ignoreFirstLine ? 1 : 0), line -> {
			if(StringUtil.isEmptyOrSpaces(line))
			{
				return "";
			}
			else
			{
				return newIndent + line.substring(oldIndent.length());
			}
		});
		if(ignoreFirstLine)
		{
			return ContainerUtil.prepend(result, Iterables.get(lines, 0));
		}
		return result;
	}

	/**
	 * Finds maximum common indentation of the given lines. Indentation of empty lines and lines containing only whitespaces is ignored unless
	 * they're the only lines provided. In the latter case common indentation for such lines is returned. If mix of tabs and spaces was used
	 * for indentation and any two of lines taken into account contain incompatible combination of these symbols, i.e. it's impossible to
	 * decide which one can be used as prefix for another, empty string is returned.
	 *
	 * @param ignoreFirstLine whether the first line should be considered (useful for multiline string literals)
	 */
	@Nonnull
	public static String findCommonIndent(@Nonnull Iterable<String> lines, boolean ignoreFirstLine)
	{
		String minIndent = null;
		boolean allLinesEmpty = true;
		if(Iterables.isEmpty(lines))
		{
			return "";
		}
		boolean hasBadEmptyLineIndent = false;
		for(String line : Iterables.skip(lines, ignoreFirstLine ? 1 : 0))
		{
			boolean lineEmpty = StringUtil.isEmptyOrSpaces(line);
			if(lineEmpty && !allLinesEmpty)
			{
				continue;
			}
			String indent = getLineIndent(line);
			if(minIndent == null || (!lineEmpty && allLinesEmpty) || minIndent.startsWith(indent))
			{
				minIndent = indent;
			}
			else if(!indent.startsWith(minIndent))
			{
				if(lineEmpty)
				{
					hasBadEmptyLineIndent = true;
				}
				else
				{
					return "";
				}
			}
			allLinesEmpty &= lineEmpty;
		}
		if(allLinesEmpty && hasBadEmptyLineIndent)
		{
			return "";
		}
		return StringUtil.notNullize(minIndent);
	}

	@Nonnull
	public static String getLineIndent(@Nonnull Document document, int lineNumber)
	{
		TextRange lineRange = TextRange.create(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
		String line = document.getText(lineRange);
		return getLineIndent(line);
	}
}
