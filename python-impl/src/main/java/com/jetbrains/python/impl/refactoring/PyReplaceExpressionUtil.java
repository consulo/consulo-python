// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring;

import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.ast.IElementType;
import consulo.util.lang.MathUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.impl.PyStringFormatParser.SubstitutionChunk;
import static com.jetbrains.python.impl.PyStringFormatParser.filterSubstitutions;
import static com.jetbrains.python.impl.PyStringFormatParser.getFormatValueExpression;
import static com.jetbrains.python.impl.PyStringFormatParser.getNewStyleFormatValueExpression;
import static com.jetbrains.python.impl.PyStringFormatParser.parseNewStyleFormat;
import static com.jetbrains.python.impl.PyStringFormatParser.parsePercentFormat;
import static com.jetbrains.python.impl.PyStringFormatParser.substitutionsToRanges;
import static com.jetbrains.python.PyTokenTypes.AND;
import static com.jetbrains.python.PyTokenTypes.AND_KEYWORD;
import static com.jetbrains.python.PyTokenTypes.AT;
import static com.jetbrains.python.PyTokenTypes.AWAIT_KEYWORD;
import static com.jetbrains.python.PyTokenTypes.COMPARISON_OPERATIONS;
import static com.jetbrains.python.PyTokenTypes.DIV;
import static com.jetbrains.python.PyTokenTypes.EXP;
import static com.jetbrains.python.PyTokenTypes.FLOORDIV;
import static com.jetbrains.python.PyTokenTypes.GTGT;
import static com.jetbrains.python.PyTokenTypes.LTLT;
import static com.jetbrains.python.PyTokenTypes.MINUS;
import static com.jetbrains.python.PyTokenTypes.MULT;
import static com.jetbrains.python.PyTokenTypes.NOT_KEYWORD;
import static com.jetbrains.python.PyTokenTypes.OR;
import static com.jetbrains.python.PyTokenTypes.OR_KEYWORD;
import static com.jetbrains.python.PyTokenTypes.PERC;
import static com.jetbrains.python.PyTokenTypes.PLUS;
import static com.jetbrains.python.PyTokenTypes.TILDE;
import static com.jetbrains.python.PyTokenTypes.XOR;

/**
 * @author Dennis.Ushakov
 */
public final class PyReplaceExpressionUtil implements PyElementTypes {
    /**
     * This marker is added in cases where valid selection nevertheless breaks existing expression.
     * It can happen in cases like (here {@code <start> and <end>} represent selection boundaries):
     * <ul>
     *   <li>Selection conflicts with operator precedence: {@code n = 1 * <start>2 + 3<end>}</li>
     *   <li>Selection conflicts with operator associativity: {@code n = 1 + <start>2 + 3<end>}</li>
     *   <li>Part of string literal is selected: {@code s = 'green <start>eggs<end> and ham'}</li>
     * </ul>
     */
    public static final Key<Pair<PsiElement, TextRange>> SELECTION_BREAKS_AST_NODE =
        new Key<>("python.selection.breaks.ast.node");

    private PyReplaceExpressionUtil() { }

    /**
     * @param oldExpr old expression that will be substituted
     * @param newExpr new expression to substitute with
     * @return whether new expression should be wrapped in parentheses to preserve original semantics
     */
    public static boolean isNeedParenthesis(PyElement oldExpr, PyElement newExpr) {
        PyElement parentExpr = (PyElement)oldExpr.getParent();
        if (parentExpr instanceof PyArgumentList) {
            return newExpr instanceof PyTupleExpression;
        }
        if (parentExpr instanceof PyParenthesizedExpression || !(parentExpr instanceof PyExpression)) {
            return false;
        }
        final int newPriority = getExpressionPriority(newExpr);
        final int parentPriority = getExpressionPriority(parentExpr);
        if (parentPriority > newPriority) {
            return true;
        }
        else if (parentPriority == newPriority && parentPriority != 0 && parentExpr instanceof PyBinaryExpression binaryExpression) {
            if (isNotAssociative(binaryExpression) && oldExpr == getLeastPrioritySide(binaryExpression)) {
                return true;
            }
        }
        else if (newExpr instanceof PyConditionalExpression && parentExpr instanceof PyConditionalExpression) {
            return true;
        }
        return false;
    }

    private static @Nullable PyExpression getLeastPrioritySide(PyBinaryExpression expression) {
        if (expression.isOperator("**")) {
            return expression.getLeftExpression();
        }
        else {
            return expression.getRightExpression();
        }
    }

    public static PsiElement replaceExpression(PsiElement oldExpression,
                                                 PsiElement newExpression) {
        Pair<PsiElement, TextRange> data = oldExpression.getUserData(SELECTION_BREAKS_AST_NODE);
        if (data != null) {
            PsiElement element = data.first;
            TextRange textRange = data.second;
            String parentText = element.getText();
            String prefix = parentText.substring(0, textRange.getStartOffset());
            String suffix = parentText.substring(textRange.getEndOffset(), element.getTextLength());
            PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
            LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
            if (element instanceof PyStringLiteralExpression) {
                return replaceSubstringInStringLiteral((PyStringLiteralExpression)element, newExpression, textRange);
            }
            PyElement newElement = generator.createFromText(languageLevel, PyElement.class, prefix + newExpression.getText() + suffix);
            return element.replace(newElement);
        }
        else {
            return oldExpression.replace(newExpression);
        }
    }

    private static @Nullable PsiElement replaceSubstringInStringLiteral(PyStringLiteralExpression oldExpression,
                                                                          PsiElement newExpression,
                                                                          TextRange textRange) {
        String fullText = oldExpression.getText();
        Pair<String, String> detectedQuotes = PyStringLiteralCoreUtil.getQuotes(fullText);
        Pair<String, String> quotes = detectedQuotes != null ? detectedQuotes : Pair.create("'", "'");
        String prefix = fullText.substring(0, textRange.getStartOffset());
        String suffix = fullText.substring(textRange.getEndOffset(), oldExpression.getTextLength());
        PyExpression formatValue = getFormatValueExpression(oldExpression);
        PyArgumentList newStyleFormatValue = getNewStyleFormatValueExpression(oldExpression);
        String newText = newExpression.getText();

        List<SubstitutionChunk> substitutions;
        if (newStyleFormatValue != null) {
            substitutions = filterSubstitutions(parseNewStyleFormat(fullText));
        }
        else {
            substitutions = filterSubstitutions(parsePercentFormat(fullText));
        }
        final boolean hasSubstitutions = !substitutions.isEmpty();

        if (formatValue != null && !containsStringFormatting(substitutions, textRange)) {
            if (formatValue instanceof PyTupleExpression) {
                return replaceSubstringWithTupleFormatting(oldExpression, newExpression, textRange, prefix, suffix,
                                                                   (PyTupleExpression)formatValue, substitutions);
            }
            else if (formatValue instanceof PyDictLiteralExpression) {
                return replaceSubstringWithDictFormatting(oldExpression, quotes, prefix, suffix, formatValue, newText);
            }
            else {
                TypeEvalContext context = TypeEvalContext.userInitiated(oldExpression.getProject(), oldExpression.getContainingFile());
                PyType valueType = context.getType(formatValue);
                PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(oldExpression);
                PyType tupleType = builtinCache.getTupleType();
                PyType mappingType = PyLegacyDocstringTypeParser.getTypeByName(oldExpression, "collections.abc.Mapping", context);
                if (!PyTypeChecker.match(tupleType, valueType, context) &&
                    !(mappingType != null && PyTypeChecker.match(mappingType, valueType, context))) {
                    return replaceSubstringWithSingleValueFormatting(oldExpression, textRange, prefix, suffix, formatValue, newText, substitutions);
                }
            }
        }

        if (newStyleFormatValue != null && hasSubstitutions && !containsStringFormatting(substitutions, textRange)) {
            PyExpression[] arguments = newStyleFormatValue.getArguments();
            boolean hasStarArguments = false;
            for (PyExpression argument : arguments) {
                if (argument instanceof PyStarArgument) {
                    hasStarArguments = true;
                    break;
                }
            }
            if (!hasStarArguments) {
                return replaceSubstringWithNewStyleFormatting(oldExpression, textRange, prefix, suffix, newStyleFormatValue, newText,
                                                                      substitutions);
            }
        }

        if (isConcatFormatting(oldExpression) || hasSubstitutions) {
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
                                                                          List<SubstitutionChunk> substitutions) {
        // 'foo%s' % value if value is not tuple or mapping -> '%s%s' % (s, value)
        PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
        String newLiteralText = prefix + "%s" + suffix;
        PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
        oldExpression.replace(newLiteralExpression);
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        final int i = getPositionInRanges(substitutionsToRanges(substitutions), textRange);
        final int pos;
        if (i == 0) {
            pos = builder.toString().length();
            builder.append(newText);
            builder.append(",");
            builder.append(formatValue.getText());
        }
        else {
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
                                                                   String newText) {
        // 'foo%(x)s' % {'x': x} -> '%(s)s%(x)s' % {'x': x, 's': s}
        // TODO: Support the dict() function
        PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
        String newLiteralText = prefix + "%(" + newText + ")s" + suffix;
        PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
        oldExpression.replace(newLiteralExpression);

        PyDictLiteralExpression dict = (PyDictLiteralExpression)formatValue;
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        PyKeyValueExpression[] elements = dict.getElements();
        builder.append(StringUtil.join(elements, expression -> expression.getText(), ","));
        if (elements.length > 0) {
            builder.append(",");
        }
        builder.append(quotes.getSecond());
        builder.append(newText);
        builder.append(quotes.getSecond());
        builder.append(":");
        final int pos = builder.toString().length();
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
                                                                    List<SubstitutionChunk> substitutions) {
        // 'foo%s' % (x,) -> '%s%s' % (s, x)
        String newLiteralText = prefix + "%s" + suffix;
        PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
        PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
        oldExpression.replace(newLiteralExpression);

        PyExpression[] members = tupleFormatValue.getElements();
        final int n = members.length;
        final int i = MathUtil.clamp(getPositionInRanges(substitutionsToRanges(substitutions), textRange), 0, n);
        final boolean last = i == n;
        PsiElement trailingComma = PyPsiUtils.getNextComma(members[n - 1]);
        if (trailingComma != null) {
            trailingComma.delete();
        }
        PyExpression before = last ? null : members[i];
        PyUtil.addListNode(tupleFormatValue, newExpression, before != null ? before.getNode() : null, i == 0 || !last, last, !last);
        return newExpression;
    }

    private static PsiElement replaceSubstringWithoutFormatting(PyStringLiteralExpression oldExpression,
                                                                  String prefix,
                                                                  String suffix,
                                                                  String newText) {
        // 'foobar' -> '%sbar' % s
        PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
        PsiElement parent = oldExpression.getParent();
        final boolean parensNeeded = parent instanceof PyExpression && !(parent instanceof PyParenthesizedExpression);
        StringBuilder builder = new StringBuilder();
        if (parensNeeded) {
            builder.append("(");
        }
        builder.append(prefix);
        builder.append("%s");
        builder.append(suffix);
        builder.append(" % ");
        final int pos = builder.toString().length();
        builder.append(newText);
        if (parensNeeded) {
            builder.append(")");
        }
        PyExpression expression = generator.createExpressionFromText(languageLevel, builder.toString());
        PsiElement newElement = oldExpression.replace(expression);
        return newElement.findElementAt(pos);
    }

    private static PsiElement replaceSubstringWithConcatFormatting(PyStringLiteralExpression oldExpression,
                                                                     Pair<String, String> quotes,
                                                                     String prefix,
                                                                     String suffix,
                                                                     String newText,
                                                                     boolean hasSubstitutions) {
        // 'foobar' + 'baz' -> s + 'bar' + 'baz'
        // 'foobar%s' -> s + 'bar%s'
        // 'f%soobar' % x -> (s + 'bar') % x
        PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
        String leftQuote = quotes.getFirst();
        String rightQuote = quotes.getSecond();
        StringBuilder builder = new StringBuilder();
        if (hasSubstitutions) {
            builder.append("(");
        }
        if (!leftQuote.endsWith(prefix)) {
            builder.append(prefix).append(rightQuote).append(" + ");
        }
        final int pos = builder.toString().length();
        builder.append(newText);
        if (!rightQuote.startsWith(suffix)) {
            builder.append(" + ").append(leftQuote).append(suffix);
        }
        if (hasSubstitutions) {
            builder.append(")");
        }
        PsiElement expression = generator.createExpressionFromText(languageLevel, builder.toString());
        PsiElement newElement = oldExpression.replace(expression);
        return newElement.findElementAt(pos);
    }

    private static PsiElement replaceSubstringWithNewStyleFormatting(PyStringLiteralExpression oldExpression,
                                                                       TextRange textRange,
                                                                       String prefix,
                                                                       String suffix,
                                                                       PyArgumentList newStyleFormatValue,
                                                                       String newText,
                                                                       List<SubstitutionChunk> substitutions) {
        PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
        PyExpression[] arguments = newStyleFormatValue.getArguments();
        boolean hasKeywords = false;
        int maxPosition = -1;
        for (SubstitutionChunk substitution : substitutions) {
            if (substitution.getMappingKey() != null) {
                hasKeywords = true;
            }
            Integer position = substitution.getManualPosition();
            if (position != null && position > maxPosition) {
                maxPosition = position;
            }
        }
        if (hasKeywords) {
            // 'foo{x}'.format(x='bar') -> '{s}oo{x}'.format(x='bar', s=s)
            String newLiteralText = prefix + "{" + newText + "}" + suffix;
            PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
            oldExpression.replace(newLiteralExpression);

            PyKeywordArgument kwarg = generator.createKeywordArgument(languageLevel, newText, newText);
            newStyleFormatValue.addArgument(kwarg);
            return kwarg.getValueExpression();
        }
        else if (maxPosition >= 0) {
            // 'foo{0}'.format('bar') -> '{1}oo{0}'.format('bar', s)
            String newLiteralText = prefix + "{" + (maxPosition + 1) + "}" + suffix;
            PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
            oldExpression.replace(newLiteralExpression);

            PyExpression arg = generator.createExpressionFromText(languageLevel, newText);
            newStyleFormatValue.addArgument(arg);
            return arg;
        }
        else {
            // 'foo{}'.format('bar') -> '{}oo{}'.format(s, 'bar')
            String newLiteralText = prefix + "{}" + suffix;
            PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
            oldExpression.replace(newLiteralExpression);
            final int i = getPositionInRanges(substitutionsToRanges(substitutions), textRange);
            PyExpression arg = generator.createExpressionFromText(languageLevel, newText);
            if (i == 0) {
                newStyleFormatValue.addArgumentFirst(arg);
            }
            else if (i < arguments.length) {
                newStyleFormatValue.addArgumentAfter(arg, arguments[i - 1]);
            }
            else {
                newStyleFormatValue.addArgument(arg);
            }
            return arg;
        }
    }

    private static int getPositionInRanges(List<TextRange> ranges, TextRange range) {
        final int end = range.getEndOffset();
        final int size = ranges.size();
        for (int i = 0; i < size; i++) {
            TextRange r = ranges.get(i);
            if (end < r.getStartOffset()) {
                return i;
            }
        }
        return size;
    }

    private static boolean containsStringFormatting(List<SubstitutionChunk> substitutions,
                                                      TextRange range) {
        List<TextRange> ranges = substitutionsToRanges(substitutions);
        for (TextRange r : ranges) {
            if (range.contains(r)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConcatFormatting(PyStringLiteralExpression element) {
        PsiElement parent = element.getParent();
        return parent instanceof PyBinaryExpression && ((PyBinaryExpression)parent).isOperator("+");
    }

    private static boolean isNotAssociative(PyBinaryExpression binaryExpression) {
        IElementType opType = getOperationType(binaryExpression);
        return COMPARISON_OPERATIONS.contains(opType) ||
                   opType == DIV || opType == FLOORDIV || opType == PERC || opType == EXP || opType == MINUS;
    }

    private static int getExpressionPriority(PyElement expr) {
        int priority = 0;
        if (expr instanceof PyReferenceExpression ||
            expr instanceof PySubscriptionExpression ||
            expr instanceof PyCallExpression) {
            priority = 1;
        }
        else if (expr instanceof PyPrefixExpression) {
            IElementType opType = getOperationType(expr);
            if (opType == AWAIT_KEYWORD) priority = 2;
            if (opType == PLUS || opType == MINUS || opType == TILDE) priority = 3;
            if (opType == NOT_KEYWORD) priority = 12;
        }
        else if (expr instanceof PyBinaryExpression) {
            IElementType opType = getOperationType(expr);
            if (opType == EXP) priority = 4;
            if (opType == MULT || opType == AT || opType == DIV || opType == PERC || opType == FLOORDIV) priority = 5;
            if (opType == PLUS || opType == MINUS) priority = 6;
            if (opType == LTLT || opType == GTGT) priority = 7;
            if (opType == AND) priority = 8;
            if (opType == XOR) priority = 9;
            if (opType == OR) priority = 10;
            if (COMPARISON_OPERATIONS.contains(opType)) priority = 11;
            if (opType == AND_KEYWORD) priority = 13;
            if (opType == OR_KEYWORD) priority = 14;
        }
        else if (expr instanceof PyConditionalExpression) {
            priority = 15;
        }
        else if (expr instanceof PyLambdaExpression) {
            priority = 16;
        }
        else if (expr instanceof PyAssignmentExpression) priority = 17;

        return -priority;
    }

    private static @Nullable IElementType getOperationType(PyElement expr) {
        if (expr instanceof PyBinaryExpression) return ((PyBinaryExpression)expr).getOperator();
        return ((PyPrefixExpression)expr).getOperator();
    }
}
