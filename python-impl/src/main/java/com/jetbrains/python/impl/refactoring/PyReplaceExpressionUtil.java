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
package com.jetbrains.python.impl.refactoring;

import static com.jetbrains.python.PyTokenTypes.*;
import static com.jetbrains.python.impl.inspections.PyStringFormatParser.filterSubstitutions;
import static com.jetbrains.python.impl.inspections.PyStringFormatParser.parseNewStyleFormat;
import static com.jetbrains.python.impl.inspections.PyStringFormatParser.parsePercentFormat;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.ast.IElementType;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author Dennis.Ushakov
 */
public class PyReplaceExpressionUtil implements PyElementTypes
{
	/**
	 * This marker is added in cases where valid selection nevertheless breaks existing expression.
	 * It can happen in cases like (here {@code <start> and <end>} represent selection boundaries):
	 * <ul>
	 * <li>Selection conflicts with operator precedence: {@code n = 1 * <start>2 + 3<end>}</li>
	 * <li>Selection conflicts with operator associativity: {@code n = 1 + <start>2 + 3<end>}</li>
	 * <li>Part of string literal is selected: {@code s = 'green <start>eggs<end> and ham'}</li>
	 * </ul>
	 */
	public static final Key<Pair<PsiElement, TextRange>> SELECTION_BREAKS_AST_NODE = new Key<>("python.selection.breaks.ast.node");

	private PyReplaceExpressionUtil()
	{
	}

	/**
	 * @param oldExpr old expression that will be substituted
	 * @param newExpr new expression to substitute with
	 * @return whether new expression should be wrapped in parenthesis to preserve original semantics
	 */
	public static boolean isNeedParenthesis(@Nonnull PyElement oldExpr, @Nonnull PyElement newExpr)
	{
		PyElement parentExpr = (PyElement) oldExpr.getParent();
		if(parentExpr instanceof PyArgumentList)
		{
			return newExpr instanceof PyTupleExpression;
		}
		if(parentExpr instanceof PyParenthesizedExpression || !(parentExpr instanceof PyExpression))
		{
			return false;
		}
		int newPriority = getExpressionPriority(newExpr);
		int parentPriority = getExpressionPriority(parentExpr);
		if(parentPriority > newPriority)
		{
			return true;
		}
		else if(parentPriority == newPriority && parentPriority != 0 && parentExpr instanceof PyBinaryExpression)
		{
			PyBinaryExpression binaryExpression = (PyBinaryExpression) parentExpr;
			if(isNotAssociative(binaryExpression) && oldExpr == getLeastPrioritySide(binaryExpression))
			{
				return true;
			}
		}
		else if(newExpr instanceof PyConditionalExpression && parentExpr instanceof PyConditionalExpression)
		{
			return true;
		}
		return false;
	}

	@Nullable
	private static PyExpression getLeastPrioritySide(@Nonnull PyBinaryExpression expression)
	{
		if(expression.isOperator("**"))
		{
			return expression.getLeftExpression();
		}
		else
		{
			return expression.getRightExpression();
		}
	}

	public static PsiElement replaceExpression(@Nonnull PsiElement oldExpression, @Nonnull PsiElement newExpression)
	{
		Pair<PsiElement, TextRange> data = oldExpression.getUserData(SELECTION_BREAKS_AST_NODE);
		if(data != null)
		{
			PsiElement element = data.first;
			TextRange textRange = data.second;
			String parentText = element.getText();
			String prefix = parentText.substring(0, textRange.getStartOffset());
			String suffix = parentText.substring(textRange.getEndOffset(), element.getTextLength());
			PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
			LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
			if(element instanceof PyStringLiteralExpression)
			{
				return replaceSubstringInStringLiteral((PyStringLiteralExpression) element, newExpression, textRange);
			}
			PsiElement expression = generator.createFromText(languageLevel, element.getClass(), prefix + newExpression.getText() + suffix);
			return element.replace(expression);
		}
		else
		{
			return oldExpression.replace(newExpression);
		}
	}

	@Nullable
	private static PsiElement replaceSubstringInStringLiteral(@Nonnull PyStringLiteralExpression oldExpression, @Nonnull PsiElement newExpression, @Nonnull TextRange textRange)
	{
		String fullText = oldExpression.getText();
		Pair<String, String> detectedQuotes = PyStringLiteralUtil.getQuotes(fullText);
		Pair<String, String> quotes = detectedQuotes != null ? detectedQuotes : Pair.create("'", "'");
		String prefix = fullText.substring(0, textRange.getStartOffset());
		String suffix = fullText.substring(textRange.getEndOffset(), oldExpression.getTextLength());
		PyExpression formatValue = PyStringFormatParser.getFormatValueExpression(oldExpression);
		PyArgumentList newStyleFormatValue = PyStringFormatParser.getNewStyleFormatValueExpression(oldExpression);
		String newText = newExpression.getText();

		List<PyStringFormatParser.SubstitutionChunk> substitutions;
		if(newStyleFormatValue != null)
		{
			substitutions = filterSubstitutions(parseNewStyleFormat(fullText));
		}
		else
		{
			substitutions = filterSubstitutions(parsePercentFormat(fullText));
		}
		boolean hasSubstitutions = substitutions.size() > 0;

		if(formatValue != null && !containsStringFormatting(substitutions, textRange))
		{
			if(formatValue instanceof PyTupleExpression)
			{
				return replaceSubstringWithTupleFormatting(oldExpression, newExpression, textRange, prefix, suffix, (PyTupleExpression) formatValue, substitutions);
			}
			else if(formatValue instanceof PyDictLiteralExpression)
			{
				return replaceSubstringWithDictFormatting(oldExpression, quotes, prefix, suffix, formatValue, newText);
			}
			else
			{
				TypeEvalContext context = TypeEvalContext.userInitiated(oldExpression.getProject(), oldExpression.getContainingFile());
				PyType valueType = context.getType(formatValue);
				PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(oldExpression);
				PyType tupleType = builtinCache.getTupleType();
				PyType mappingType = PyTypeParser.getTypeByName(null, "collections.Mapping");
				if(!PyTypeChecker.match(tupleType, valueType, context) || (mappingType != null && !PyTypeChecker.match(mappingType, valueType, context)))
				{
					return replaceSubstringWithSingleValueFormatting(oldExpression, textRange, prefix, suffix, formatValue, newText, substitutions);
				}
			}
		}

		if(newStyleFormatValue != null && hasSubstitutions && !containsStringFormatting(substitutions, textRange))
		{
			PyExpression[] arguments = newStyleFormatValue.getArguments();
			boolean hasStarArguments = false;
			for(PyExpression argument : arguments)
			{
				if(argument instanceof PyStarArgument)
				{
					hasStarArguments = true;
				}
			}
			if(!hasStarArguments)
			{
				return replaceSubstringWithNewStyleFormatting(oldExpression, textRange, prefix, suffix, newStyleFormatValue, newText, substitutions);
			}
		}

		if(isConcatFormatting(oldExpression) || hasSubstitutions)
		{
			return replaceSubstringWithConcatFormatting(oldExpression, quotes, prefix, suffix, newText, hasSubstitutions);
		}

		return replaceSubstringWithoutFormatting(oldExpression, prefix, suffix, newText);
	}

	private static PsiElement replaceSubstringWithSingleValueFormatting(PyStringLiteralExpression oldExpression,
			TextRange textRange,
			String prefix,
			String suffix,
			PyExpression formatValue,
			String newText,
			List<PyStringFormatParser.SubstitutionChunk> substitutions)
	{
		// 'foo%s' % value if value is not tuple or mapping -> '%s%s' % (s, value)
		PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
		LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
		String newLiteralText = prefix + "%s" + suffix;
		PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
		oldExpression.replace(newLiteralExpression);
		StringBuilder builder = new StringBuilder();
		builder.append("(");
		int i = getPositionInRanges(PyStringFormatParser.substitutionsToRanges(substitutions), textRange);
		int pos;
		if(i == 0)
		{
			pos = builder.toString().length();
			builder.append(newText);
			builder.append(",");
			builder.append(formatValue.getText());
		}
		else
		{
			builder.append(formatValue.getText());
			builder.append(",");
			pos = builder.toString().length();
			builder.append(newText);
		}
		builder.append(")");
		PsiElement newElement = formatValue.replace(generator.createExpressionFromText(languageLevel, builder.toString()));
		return newElement.findElementAt(pos);
	}

	private static PsiElement replaceSubstringWithDictFormatting(PyStringLiteralExpression oldExpression,
			Pair<String, String> quotes,
			String prefix,
			String suffix,
			PyExpression formatValue,
			String newText)
	{
		// 'foo%(x)s' % {'x': x} -> '%(s)s%(x)s' % {'x': x, 's': s}
		// TODO: Support the dict() function
		PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
		LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
		String newLiteralText = prefix + "%(" + newText + ")s" + suffix;
		PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
		oldExpression.replace(newLiteralExpression);

		PyDictLiteralExpression dict = (PyDictLiteralExpression) formatValue;
		StringBuilder builder = new StringBuilder();
		builder.append("{");
		PyKeyValueExpression[] elements = dict.getElements();
		builder.append(StringUtil.join(elements, expression -> expression.getText(), ","));
		if(elements.length > 0)
		{
			builder.append(",");
		}
		builder.append(quotes.getSecond());
		builder.append(newText);
		builder.append(quotes.getSecond());
		builder.append(":");
		int pos = builder.toString().length();
		builder.append(newText);
		builder.append("}");
		PyExpression newDictLiteral = generator.createExpressionFromText(languageLevel, builder.toString());
		PsiElement newElement = formatValue.replace(newDictLiteral);
		return newElement.findElementAt(pos);
	}

	private static PsiElement replaceSubstringWithTupleFormatting(PyStringLiteralExpression oldExpression,
			PsiElement newExpression,
			TextRange textRange,
			String prefix,
			String suffix,
			PyTupleExpression tupleFormatValue,
			List<PyStringFormatParser.SubstitutionChunk> substitutions)
	{
		// 'foo%s' % (x,) -> '%s%s' % (s, x)
		String newLiteralText = prefix + "%s" + suffix;
		PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
		PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
		oldExpression.replace(newLiteralExpression);

		PyExpression[] members = tupleFormatValue.getElements();
		int n = members.length;
		int i = Math.min(n, Math.max(0, getPositionInRanges(PyStringFormatParser.substitutionsToRanges(substitutions), textRange)));
		boolean last = i == n;
		PsiElement trailingComma = PyPsiUtils.getNextComma(members[n - 1]);
		if(trailingComma != null)
		{
			trailingComma.delete();
		}
		PyExpression before = last ? null : members[i];
		PyUtil.addListNode(tupleFormatValue, newExpression, before != null ? before.getNode() : null, i == 0 || !last, last, !last);
		return newExpression;
	}

	private static PsiElement replaceSubstringWithoutFormatting(@Nonnull PyStringLiteralExpression oldExpression, @Nonnull String prefix, @Nonnull String suffix, @Nonnull String newText)
	{
		// 'foobar' -> '%sbar' % s
		PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
		LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
		PsiElement parent = oldExpression.getParent();
		boolean parensNeeded = parent instanceof PyExpression && !(parent instanceof PyParenthesizedExpression);
		StringBuilder builder = new StringBuilder();
		if(parensNeeded)
		{
			builder.append("(");
		}
		builder.append(prefix);
		builder.append("%s");
		builder.append(suffix);
		builder.append(" % ");
		int pos = builder.toString().length();
		builder.append(newText);
		if(parensNeeded)
		{
			builder.append(")");
		}
		PyExpression expression = generator.createExpressionFromText(languageLevel, builder.toString());
		PsiElement newElement = oldExpression.replace(expression);
		return newElement.findElementAt(pos);
	}

	private static PsiElement replaceSubstringWithConcatFormatting(@Nonnull PyStringLiteralExpression oldExpression,
			@Nonnull Pair<String, String> quotes,
			@Nonnull String prefix,
			@Nonnull String suffix,
			@Nonnull String newText,
			boolean hasSubstitutions)
	{
		// 'foobar' + 'baz' -> s + 'bar' + 'baz'
		// 'foobar%s' -> s + 'bar%s'
		// 'f%soobar' % x -> (s + 'bar') % x
		PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
		LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
		String leftQuote = quotes.getFirst();
		String rightQuote = quotes.getSecond();
		StringBuilder builder = new StringBuilder();
		if(hasSubstitutions)
		{
			builder.append("(");
		}
		if(!leftQuote.endsWith(prefix))
		{
			builder.append(prefix + rightQuote + " + ");
		}
		int pos = builder.toString().length();
		builder.append(newText);
		if(!rightQuote.startsWith(suffix))
		{
			builder.append(" + " + leftQuote + suffix);
		}
		if(hasSubstitutions)
		{
			builder.append(")");
		}
		PsiElement expression = generator.createExpressionFromText(languageLevel, builder.toString());
		PsiElement newElement = oldExpression.replace(expression);
		return newElement.findElementAt(pos);
	}

	private static PsiElement replaceSubstringWithNewStyleFormatting(@Nonnull PyStringLiteralExpression oldExpression,
			@Nonnull TextRange textRange,
			@Nonnull String prefix,
			@Nonnull String suffix,
			@Nonnull PyArgumentList newStyleFormatValue,
			@Nonnull String newText,
			@Nonnull List<PyStringFormatParser.SubstitutionChunk> substitutions)
	{
		PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
		LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
		PyExpression[] arguments = newStyleFormatValue.getArguments();
		boolean hasKeywords = false;
		int maxPosition = -1;
		for(PyStringFormatParser.SubstitutionChunk substitution : substitutions)
		{
			if(substitution.getMappingKey() != null)
			{
				hasKeywords = true;
			}
			Integer position = substitution.getPosition();
			if(position != null && position > maxPosition)
			{
				maxPosition = position;
			}
		}
		if(hasKeywords)
		{
			// 'foo{x}'.format(x='bar') -> '{s}oo{x}'.format(x='bar', s=s)
			String newLiteralText = prefix + "{" + newText + "}" + suffix;
			PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
			oldExpression.replace(newLiteralExpression);

			PyKeywordArgument kwarg = generator.createKeywordArgument(languageLevel, newText, newText);
			newStyleFormatValue.addArgument(kwarg);
			return kwarg.getValueExpression();
		}
		else if(maxPosition >= 0)
		{
			// 'foo{0}'.format('bar') -> '{1}oo{0}'.format('bar', s)
			String newLiteralText = prefix + "{" + (maxPosition + 1) + "}" + suffix;
			PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
			oldExpression.replace(newLiteralExpression);

			PyExpression arg = generator.createExpressionFromText(languageLevel, newText);
			newStyleFormatValue.addArgument(arg);
			return arg;
		}
		else
		{
			// 'foo{}'.format('bar') -> '{}oo{}'.format(s, 'bar')
			String newLiteralText = prefix + "{}" + suffix;
			PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
			oldExpression.replace(newLiteralExpression);
			int i = getPositionInRanges(PyStringFormatParser.substitutionsToRanges(substitutions), textRange);
			PyExpression arg = generator.createExpressionFromText(languageLevel, newText);
			if(i == 0)
			{
				newStyleFormatValue.addArgumentFirst(arg);
			}
			else if(i < arguments.length)
			{
				newStyleFormatValue.addArgumentAfter(arg, arguments[i - 1]);
			}
			else
			{
				newStyleFormatValue.addArgument(arg);
			}
			return arg;
		}
	}

	private static int getPositionInRanges(@Nonnull List<TextRange> ranges, @Nonnull TextRange range)
	{
		int end = range.getEndOffset();
		int size = ranges.size();
		for(int i = 0; i < size; i++)
		{
			TextRange r = ranges.get(i);
			if(end < r.getStartOffset())
			{
				return i;
			}
		}
		return size;
	}

	private static boolean containsStringFormatting(@Nonnull List<PyStringFormatParser.SubstitutionChunk> substitutions, @Nonnull TextRange range)
	{
		List<TextRange> ranges = PyStringFormatParser.substitutionsToRanges(substitutions);
		for(TextRange r : ranges)
		{
			if(range.contains(r))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isConcatFormatting(PyStringLiteralExpression element)
	{
		PsiElement parent = element.getParent();
		return parent instanceof PyBinaryExpression && ((PyBinaryExpression) parent).isOperator("+");
	}

	private static boolean isNotAssociative(@Nonnull PyBinaryExpression binaryExpression)
	{
		IElementType opType = getOperationType(binaryExpression);
		return COMPARISON_OPERATIONS.contains(opType) || binaryExpression instanceof PySliceExpression ||
				opType == DIV || opType == FLOORDIV || opType == PERC || opType == EXP || opType == MINUS;
	}

	private static int getExpressionPriority(PyElement expr)
	{
		int priority = 0;
		if(expr instanceof PyReferenceExpression ||
				expr instanceof PySubscriptionExpression ||
				expr instanceof PySliceExpression ||
				expr instanceof PyCallExpression)
		{
			priority = 1;
		}
		else if(expr instanceof PyPrefixExpression)
		{
			IElementType opType = getOperationType(expr);
			if(opType == PLUS || opType == MINUS || opType == TILDE)
			{
				priority = 2;
			}
			if(opType == NOT_KEYWORD)
			{
				priority = 11;
			}
		}
		else if(expr instanceof PyBinaryExpression)
		{
			IElementType opType = getOperationType(expr);
			if(opType == EXP)
			{
				priority = 3;
			}
			if(opType == MULT || opType == AT || opType == DIV || opType == PERC || opType == FLOORDIV)
			{
				priority = 4;
			}
			if(opType == PLUS || opType == MINUS)
			{
				priority = 5;
			}
			if(opType == LTLT || opType == GTGT)
			{
				priority = 6;
			}
			if(opType == AND)
			{
				priority = 7;
			}
			if(opType == XOR)
			{
				priority = 8;
			}
			if(opType == OR)
			{
				priority = 9;
			}
			if(COMPARISON_OPERATIONS.contains(opType))
			{
				priority = 10;
			}
			if(opType == AND_KEYWORD)
			{
				priority = 12;
			}
			if(opType == OR_KEYWORD)
			{
				priority = 13;
			}
		}
		else if(expr instanceof PyConditionalExpression)
		{
			priority = 14;
		}
		else if(expr instanceof PyLambdaExpression)
		{
			priority = 15;
		}

		return -priority;
	}

	@Nullable
	private static IElementType getOperationType(@Nonnull PyElement expr)
	{
		if(expr instanceof PyBinaryExpression)
		{
			return ((PyBinaryExpression) expr).getOperator();
		}
		return ((PyPrefixExpression) expr).getOperator();
	}
}
