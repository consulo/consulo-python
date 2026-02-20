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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * @author Alexey.Ivanov
 */
public class PyStringConcatenationToFormatIntention extends PyBaseIntentionAction {
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        PsiElement element =
            PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);

        if (element == null) {
            return false;
        }
        while (element.getParent() instanceof PyBinaryExpression) {
            element = element.getParent();
        }

        Collection<PyElementType> operators = getOperators((PyBinaryExpression) element);
        for (PyElementType operator : operators) {
            if (operator != PyTokenTypes.PLUS) {
                return false;
            }
        }

        Collection<PyExpression> expressions = getSimpleExpressions((PyBinaryExpression) element);
        if (expressions.size() == 0) {
            return false;
        }
        PyBuiltinCache cache = PyBuiltinCache.getInstance(element);
        for (PyExpression expression : expressions) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PyStringLiteralExpression) {
                continue;
            }
            PyType type = TypeEvalContext.codeAnalysis(file.getProject(), file).getType(expression);
            boolean isStringReference = PyTypeChecker.match(
                cache.getStringType(LanguageLevel.forElement(expression)),
                type,
                TypeEvalContext.codeAnalysis(file.getProject(), file)
            ) && type !=
                null;
            if (!isStringReference) {
                return false;
            }
        }
        if (LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON27)) {
            setText(PyLocalize.intnReplacePlusWithStrFormat());
        }
        else {
            setText(PyLocalize.intnReplacePlusWithFormatOperator());
        }
        return true;
    }

    private static Collection<PyExpression> getSimpleExpressions(@Nonnull PyBinaryExpression expression) {
        List<PyExpression> res = new ArrayList<>();
        if (expression.getLeftExpression() instanceof PyBinaryExpression) {
            res.addAll(getSimpleExpressions((PyBinaryExpression) expression.getLeftExpression()));
        }
        else {
            res.add(expression.getLeftExpression());
        }
        if (expression.getRightExpression() instanceof PyBinaryExpression) {
            res.addAll(getSimpleExpressions((PyBinaryExpression) expression.getRightExpression()));
        }
        else {
            res.add(expression.getRightExpression());
        }
        return res;
    }

    private static Collection<PyElementType> getOperators(@Nonnull PyBinaryExpression expression) {
        List<PyElementType> res = new ArrayList<>();
        if (expression.getLeftExpression() instanceof PyBinaryExpression) {
            res.addAll(getOperators((PyBinaryExpression) expression.getLeftExpression()));
        }
        if (expression.getRightExpression() instanceof PyBinaryExpression) {
            res.addAll(getOperators((PyBinaryExpression) expression.getRightExpression()));
        }
        res.add(expression.getOperator());
        return res;
    }

    @Override
    @RequiredWriteAction
    public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement element =
            PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class);

        if (element == null) {
            return;
        }
        LanguageLevel languageLevel = LanguageLevel.forElement(element);
        boolean useFormatMethod = languageLevel.isAtLeast(LanguageLevel.PYTHON27);

        Function<String, String> escaper = StringUtil.escaper(false, "\"\'\\");
        StringBuilder stringLiteral = new StringBuilder();
        List<String> parameters = new ArrayList<>();
        Pair<String, String> quotes = Couple.of("\"", "\"");
        boolean quotesDetected = false;
        TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file);
        int paramCount = 0;
        boolean isUnicode = false;
        PyClassTypeImpl unicodeType = PyBuiltinCache.getInstance(element).getObjectType("unicode");

        for (PyExpression expression : getSimpleExpressions((PyBinaryExpression) element)) {
            if (expression instanceof PyStringLiteralExpression stringLiteral1) {
                PyType type = context.getType(expression);
                if (type != null && type.equals(unicodeType)) {
                    isUnicode = true;
                }
                if (!quotesDetected) {
                    quotes = PyStringLiteralUtil.getQuotes(expression.getText());
                    quotesDetected = true;
                }
                String value = stringLiteral1.getStringValue();
                if (!useFormatMethod) {
                    value = value.replace("%", "%%");
                }
                stringLiteral.append(escaper.apply(value));
            }
            else {
                addParamToString(stringLiteral, paramCount, useFormatMethod);
                parameters.add(expression.getText());
                ++paramCount;
            }
        }
        if (quotes == null) {
            quotes = Couple.of("\"", "\"");
        }
        stringLiteral.insert(0, quotes.getFirst());
        if (isUnicode && !quotes.getFirst().toLowerCase().contains("u")) {
            stringLiteral.insert(0, "u");
        }
        stringLiteral.append(quotes.getSecond());

        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        if (!parameters.isEmpty()) {
            if (useFormatMethod) {
                stringLiteral.append(".format(").append(StringUtil.join(parameters, ",")).append(")");

            }
            else {
                String paramString =
                    parameters.size() > 1 ? "(" + StringUtil.join(parameters, ",") + ")" : StringUtil.join(parameters, ",");
                stringLiteral.append(" % ").append(paramString);
            }
            PyExpression expression =
                elementGenerator.createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, stringLiteral.toString())
                    .getExpression();
            element.replace(expression);
        }
        else {
            PyStringLiteralExpression stringLiteralExpression =
                elementGenerator.createStringLiteralAlreadyEscaped(stringLiteral.toString());
            element.replace(stringLiteralExpression);
        }
    }

    private static void addParamToString(StringBuilder stringLiteral, int i, boolean useFormatMethod) {
        if (useFormatMethod) {
            stringLiteral.append("{").append(i).append("}");
        }
        else {
            stringLiteral.append("%s");
        }
    }
}
