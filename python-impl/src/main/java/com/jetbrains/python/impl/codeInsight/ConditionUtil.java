// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conditional expressions utility
 */
public final class ConditionUtil {
    private ConditionUtil() {
    }

    private static final Map<PyElementType, String> comparisonStrings = new HashMap<>();
    private static final Map<PyElementType, PyElementType> invertedComparisons = new HashMap<>();
    private static final Set<PyElementType> validConditionalOperators = new HashSet<>();

    static {
        comparisonStrings.put(PyTokenTypes.LT, "<");
        comparisonStrings.put(PyTokenTypes.GT, ">");
        comparisonStrings.put(PyTokenTypes.EQEQ, "==");
        comparisonStrings.put(PyTokenTypes.LE, "<=");
        comparisonStrings.put(PyTokenTypes.GE, ">=");
        comparisonStrings.put(PyTokenTypes.NE, "!=");
        comparisonStrings.put(PyTokenTypes.NE_OLD, "<>");

        invertedComparisons.put(PyTokenTypes.LT, PyTokenTypes.GE);
        invertedComparisons.put(PyTokenTypes.GT, PyTokenTypes.LE);
        invertedComparisons.put(PyTokenTypes.EQEQ, PyTokenTypes.NE);
        invertedComparisons.put(PyTokenTypes.LE, PyTokenTypes.GT);
        invertedComparisons.put(PyTokenTypes.GE, PyTokenTypes.LT);
        invertedComparisons.put(PyTokenTypes.NE, PyTokenTypes.EQEQ);
        invertedComparisons.put(PyTokenTypes.NE_OLD, PyTokenTypes.EQEQ);

        validConditionalOperators.add(PyTokenTypes.IS_KEYWORD);
        validConditionalOperators.add(PyTokenTypes.IN_KEYWORD);
        validConditionalOperators.add(PyTokenTypes.NOT_KEYWORD);
        validConditionalOperators.add(PyTokenTypes.OR_KEYWORD);
        validConditionalOperators.add(PyTokenTypes.AND_KEYWORD);
        validConditionalOperators.addAll(comparisonStrings.keySet());
    }

    private static <K, V> V getValue(Map<K, V> map, K key) {
        V value = map.get(key);
        if (value == null && !map.containsKey(key)) {
            throw new java.util.NoSuchElementException("Key " + key + " is missing in the map.");
        }
        return value;
    }

    public static @Nullable Pair<String, String> findComparisonNegationOperators(@Nullable PyBinaryExpression expression) {
        PyBinaryExpression comparisonExpression = findComparisonExpression(expression);
        if (comparisonExpression == null) {
            return null;
        }
        return Pair.create(getValue(comparisonStrings, comparisonExpression.getOperator()),
            getValue(comparisonStrings, getValue(invertedComparisons, comparisonExpression.getOperator())));
    }

    public static @Nullable PyBinaryExpression findComparisonExpression(@Nullable PyBinaryExpression expression) {
        PyBinaryExpression comparisonExpression = expression;
        while (comparisonExpression != null) {
            if (comparisonStrings.containsKey(comparisonExpression.getOperator())) {
                return comparisonExpression;
            }
            comparisonExpression = PsiTreeUtil.getParentOfType(comparisonExpression, PyBinaryExpression.class);
        }
        return null;
    }

    public static @Nullable PsiElement negateComparisonExpression(Project project, PsiFile file, @Nullable PyBinaryExpression expression) {
        PyBinaryExpression comparisonExpression = findComparisonExpression(expression);
        if (comparisonExpression == null) {
            return null;
        }

        LanguageLevel level = LanguageLevel.forElement(file);
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        PsiElement parent = findNonParenthesizedExpressionParent(comparisonExpression);
        PyElementType invertedOperator = getValue(invertedComparisons, comparisonExpression.getOperator());
        PyBinaryExpression invertedExpression = elementGenerator.createBinaryExpression(
            getValue(comparisonStrings, invertedOperator),
            comparisonExpression.getLeftExpression(),
            comparisonExpression.getRightExpression());

        if (parent instanceof PyPrefixExpression prefixExpression && prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            return parent.replace(invertedExpression);
        }
        else {
            return comparisonExpression.replace(elementGenerator.createExpressionFromText(level, "not " + invertedExpression.getText()));
        }
    }

    public static boolean isValidConditionExpression(PyExpression expression) {
        if (expression instanceof PyParenthesizedExpression parenthesizedExpression) {
            return parenthesizedExpression.getContainedExpression() != null &&
                isValidConditionExpression(parenthesizedExpression.getContainedExpression());
        }

        if (expression instanceof PyPrefixExpression prefixExpression && prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            return prefixExpression.getOperand() != null;
        }

        if (expression instanceof PyBinaryExpression binaryExpression) {
            if (binaryExpression.getLeftExpression() == null || binaryExpression.getRightExpression() == null) {
                return false;
            }

            if (binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD || binaryExpression.getOperator() == PyTokenTypes.AND_KEYWORD) {
                return binaryExpression.getLeftExpression() != null &&
                    binaryExpression.getRightExpression() != null &&
                    isValidConditionExpression(binaryExpression.getLeftExpression()) &&
                    isValidConditionExpression(binaryExpression.getRightExpression());
            }

            return validConditionalOperators.contains(binaryExpression.getOperator());
        }

        return true;
    }

    public static PyExpression getInvertedConditionExpression(Project project, PsiFile file, PyExpression expression) {
        LanguageLevel level = LanguageLevel.forElement(file);
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        return getInvertedConditionExpression(project, file, level, elementGenerator, expression, true);
    }

    private static PyExpression getInvertedConditionExpression(Project project,
                                                               PsiFile file,
                                                               LanguageLevel level,
                                                               PyElementGenerator generator,
                                                               PyExpression expression,
                                                               boolean isTopLevelExpression) {
        if (expression instanceof PyParenthesizedExpression parenthesizedExpression) {
            return getInvertedConditionExpression(
                project, file, level, generator, parenthesizedExpression.getContainedExpression(), isTopLevelExpression);
        }

        if (expression instanceof PyPrefixExpression prefixExpression && prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            PyExpression invertedExpression = prefixExpression.getOperand();
            return isTopLevelExpression && !requiresParentheses(invertedExpression)
                ? (PyExpression) PyPsiUtils.flattenParens(invertedExpression).copy()
                : (PyExpression) invertedExpression.copy();
        }

        if (!(expression instanceof PyBinaryExpression binaryExpression)) {
            StringBuilder expressionBuilder = new StringBuilder("not ");
            if (expression instanceof PyAssignmentExpression || requiresParentheses(expression)) {
                expressionBuilder.append("(");
                expressionBuilder.append(expression.getText());
                expressionBuilder.append(")");
            }
            else {
                expressionBuilder.append(expression.getText());
            }
            return generator.createExpressionFromText(level, expressionBuilder.toString());
        }

        if (binaryExpression.getOperator() == PyTokenTypes.IS_KEYWORD) {
            boolean isNegative = binaryExpression.getNode().findChildByType(PyTokenTypes.NOT_KEYWORD) != null;
            return generator.createBinaryExpression(
                isNegative ? "is" : "is not",
                binaryExpression.getLeftExpression(),
                binaryExpression.getRightExpression());
        }

        if (binaryExpression.getOperator() == PyTokenTypes.IN_KEYWORD) {
            return generator.createBinaryExpression(
                "not in",
                binaryExpression.getLeftExpression(),
                binaryExpression.getRightExpression());
        }

        if (binaryExpression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            if (binaryExpression.getNode().findChildByType(PyTokenTypes.IN_KEYWORD) == null) {
                throw new IncorrectOperationException("Unexpected NOT binary expression");
            }
            return generator.createBinaryExpression(
                "in",
                binaryExpression.getLeftExpression(),
                binaryExpression.getRightExpression());
        }

        if (binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
            return generator.createBinaryExpression(
                "and",
                getInvertedConditionExpression(
                    project, file, level, generator, binaryExpression.getLeftExpression(), false),
                getInvertedConditionExpression(
                    project, file, level, generator, binaryExpression.getRightExpression(),
                    false));
        }

        if (binaryExpression.getOperator() == PyTokenTypes.AND_KEYWORD) {
            List<PyExpression> adjacentExpressions = new ArrayList<>();
            PyExpression currentExpression = binaryExpression;
            while (currentExpression instanceof PyBinaryExpression current && current.getOperator() == PyTokenTypes.AND_KEYWORD) {
                adjacentExpressions.add(current.getRightExpression());
                currentExpression = current.getLeftExpression();
            }
            adjacentExpressions.add(currentExpression);
            List<PyExpression> invertedExpressions = new ArrayList<>();
            for (int i = adjacentExpressions.size() - 1; i >= 0; i--) {
                invertedExpressions.add(getInvertedConditionExpression(
                    project, file, level, generator, adjacentExpressions.get(i), false));
            }
            return createOrExpression(level, generator, invertedExpressions, isTopLevelExpression);
        }

        if (comparisonStrings.containsKey(binaryExpression.getOperator())) {
            List<PyExpression> chainedExpressions = new ArrayList<>();
            PyExpression currentExpression = binaryExpression;
            while (currentExpression instanceof PyBinaryExpression current && comparisonStrings.containsKey(current.getOperator())) {
                PyExpression leftExpression = current.getLeftExpression();
                // The left operand forms a chained comparison, e.g.
                // `left.left <= left.right < right`, which is equivalent to
                // `(left.left <= left.right) and (left.right < right)`
                // so it should be inverted as
                // `not (left.left <= left.right) or not (left.right < right)` or, after simplification,
                // `left.left > left.right or left.right >= right`
                PyExpression newLeftExpression;
                if (leftExpression instanceof PyBinaryExpression leftBinary && comparisonStrings.containsKey(leftBinary.getOperator())) {
                    newLeftExpression = leftBinary.getRightExpression() != null ? leftBinary.getRightExpression() : leftBinary;
                }
                else {
                    newLeftExpression = leftExpression;
                }
                PyElementType invertedOperator = getValue(invertedComparisons, current.getOperator());
                PyBinaryExpression invertedExpression = generator.createBinaryExpression(
                    getValue(comparisonStrings, invertedOperator), newLeftExpression, current.getRightExpression());
                chainedExpressions.add(invertedExpression);
                currentExpression = current.getLeftExpression();
            }

            Collections.reverse(chainedExpressions);
            return createOrExpression(level, generator, chainedExpressions, isTopLevelExpression);
        }

        throw new IncorrectOperationException("Is not a condition");
    }

    private static PsiElement findNonParenthesizedExpressionParent(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent instanceof PyParenthesizedExpression) {
            parent = parent.getParent();
        }
        return parent;
    }

    private static boolean requiresParentheses(PsiElement element) {
        boolean allWhitespacesAreParenthesized = true;
        for (PsiWhiteSpace whitespace : PsiTreeUtil.findChildrenOfType(element, PsiWhiteSpace.class)) {
            PsiElement found = null;
            PsiElement it = whitespace.getParent();
            while (it != null && it != element) {
                if (it instanceof PyParenthesizedExpression || it instanceof PyArgumentList) {
                    found = it;
                    break;
                }
                it = it.getParent();
            }
            if (found == null) {
                allWhitespacesAreParenthesized = false;
                break;
            }
        }
        if (allWhitespacesAreParenthesized) {
            return false;
        }

        boolean result = true;
        boolean hasTrailingBackslash = false;
        for (char c : element.getText().toCharArray()) {
            if (StringUtil.isLineBreak(c)) {
                result = result && hasTrailingBackslash;
                hasTrailingBackslash = false;
            }
            else if (c == '\\') {
                hasTrailingBackslash = true;
            }
            else if (!StringUtil.isWhiteSpace(c)) {
                hasTrailingBackslash = false;
            }
        }
        return !result;
    }

    private static PyExpression createOrExpression(LanguageLevel level,
                                                   PyElementGenerator generator,
                                                   List<PyExpression> expressions,
                                                   boolean isTopLevelExpression) {
        StringBuilder result = new StringBuilder();

        boolean requiresParentheses = !isTopLevelExpression && expressions.size() > 1;
        if (requiresParentheses) {
            result.append("(");
        }

        for (int i = 0; i < expressions.size(); i++) {
            if (i > 0) {
                result.append(" or ");
            }
            result.append(expressions.get(i).getText());
        }

        if (requiresParentheses) {
            result.append(")");
        }

        return generator.createExpressionFromText(level, result.toString());
    }
}
