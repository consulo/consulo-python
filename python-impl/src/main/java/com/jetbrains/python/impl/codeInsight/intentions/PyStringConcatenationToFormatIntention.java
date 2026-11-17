// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyStringConcatenationToFormatIntention", categories = "Python", fileExtensions = "py")
public final class PyStringConcatenationToFormatIntention extends PsiElementBaseIntentionAction {

    public PyStringConcatenationToFormatIntention() {
        setText(PyLocalize.intnStringConcatenationToFormat());
    }

    private static @Nullable PyBinaryExpression findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyBinaryExpression candidate) {
                return candidate;
            }
            if (current instanceof PsiFile) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PyBinaryExpression element = findElement(leaf);
        if (element == null) {
            return false;
        }
        LocalizeValue text = getPresentation(element);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyBinaryExpression element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }



    private @Nullable LocalizeValue getPresentation(PyBinaryExpression element) {
        if (!(element.getContainingFile() instanceof PyFile)) {
            return null;
        }

        PyBinaryExpression topmostBinaryExpr = getTopmostBinaryExpression(element);

        List<PyElementType> operators = getOperators(topmostBinaryExpr);
        for (PyElementType operator : operators) {
            if (operator != PyTokenTypes.PLUS) {
                return null;
            }
        }

        List<PyExpression> operands = getOperands(topmostBinaryExpr);
        if (operands.isEmpty()) {
            return null;
        }
        LanguageLevel languageLevel = LanguageLevel.forElement(element);
        TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
        PyType stringType = PyBuiltinCache.getInstance(element).getStringType(languageLevel);
        if (stringType == null) {
            // The project has no builtins, so the operand types cannot be compared with `str`.
            return null;
        }
        for (PyExpression operand : operands) {
            if (operand == null) {
                return null;
            }
            if (operand instanceof PyStringLiteralExpression) {
                continue;
            }
            PyType operandType = typeEvalContext.getType(operand);
            boolean isStringReference = operandType != null && PyTypeChecker.match(stringType, operandType, typeEvalContext);
            if (!isStringReference) {
                return null;
            }
        }
        if (languageLevel.isAtLeast(LanguageLevel.PYTHON27)) {
            return PyLocalize.intnReplacePlusWithStrFormat();
        }
        else {
            return PyLocalize.intnReplacePlusWithFormatOperator();
        }
    }

    private void invoke(PyBinaryExpression element, Editor editor) {
        PyBinaryExpression topmostBinaryExpr = getTopmostBinaryExpression(element);

        LanguageLevel languageLevel = LanguageLevel.forElement(topmostBinaryExpr);
        boolean useFormatMethod = languageLevel.isAtLeast(LanguageLevel.PYTHON27);

        Function<String, String> escaper = StringUtil.escaper(false, "\"'\\");
        StringBuilder stringFormatExpr = new StringBuilder();
        List<String> parameters = new ArrayList<>();
        Pair<String, String> quotes = Pair.create("\"", "\"");
        boolean quotesDetected = false;
        TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
        boolean isUnicode = false;
        PyClassType unicodeType = PyBuiltinCache.getInstance(topmostBinaryExpr).getObjectType("unicode");

        for (PyExpression operand : getOperands(topmostBinaryExpr)) {
            if (operand instanceof PyStringLiteralExpression stringLiteralOperand) {
                PyType type = context.getType(operand);
                if (type != null && type.equals(unicodeType)) {
                    isUnicode = true;
                }
                if (!quotesDetected) {
                    quotes = PyStringLiteralCoreUtil.getQuotes(operand.getText());
                    quotesDetected = true;
                }
                String value = stringLiteralOperand.getStringValue();
                if (!useFormatMethod) {
                    value = value.replace("%", "%%");
                }
                stringFormatExpr.append(escaper.apply(value));
            }
            else {
                addParamToString(stringFormatExpr, parameters.size(), useFormatMethod);
                parameters.add(operand.getText());
            }
        }
        if (quotes == null) {
            quotes = Pair.create("\"", "\"");
        }
        stringFormatExpr.insert(0, quotes.getFirst());
        if (isUnicode && !StringUtil.toLowerCase(quotes.getFirst()).contains("u")) {
            stringFormatExpr.insert(0, "u");
        }
        stringFormatExpr.append(quotes.getSecond());

        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());

        if (!parameters.isEmpty()) {
            if (useFormatMethod) {
                stringFormatExpr.append(".format(").append(StringUtil.join(parameters, ",")).append(")");
            }
            else {
                String paramString = parameters.size() > 1 ? "(" + StringUtil.join(parameters, ",") + ")"
                                                                                                      : StringUtil.join(parameters, ",");
                stringFormatExpr.append(" % ").append(paramString);
            }
            topmostBinaryExpr.replace(elementGenerator.createExpressionFromText(languageLevel, stringFormatExpr.toString()));
        }
        else {
            topmostBinaryExpr.replace(elementGenerator.createStringLiteralAlreadyEscaped(stringFormatExpr.toString()));
        }
    }

    private static PyBinaryExpression getTopmostBinaryExpression(PyBinaryExpression element) {
        PyBinaryExpression result = element;
        while (result.getParent() instanceof PyBinaryExpression binaryExpression) {
            result = binaryExpression;
        }
        return result;
    }

    private static List<PyExpression> getOperands(PyBinaryExpression expression) {
        List<PyExpression> res = new ArrayList<>();
        if (expression.getLeftExpression() instanceof PyBinaryExpression leftBinaryOperand) {
            res.addAll(getOperands(leftBinaryOperand));
        }
        else {
            res.add(expression.getLeftExpression());
        }
        if (expression.getRightExpression() instanceof PyBinaryExpression rightBinaryOperand) {
            res.addAll(getOperands(rightBinaryOperand));
        }
        else {
            res.add(expression.getRightExpression());
        }
        return res;
    }

    private static List<PyElementType> getOperators(PyBinaryExpression expression) {
        List<PyElementType> res = new ArrayList<>();
        if (expression.getLeftExpression() instanceof PyBinaryExpression leftBinaryOperand) {
            res.addAll(getOperators(leftBinaryOperand));
        }
        if (expression.getRightExpression() instanceof PyBinaryExpression rightBinaryOperand) {
            res.addAll(getOperators(rightBinaryOperand));
        }
        res.add(expression.getOperator());
        return res;
    }

    private static void addParamToString(StringBuilder stringLiteral, int index, boolean useFormatMethod) {
        if (useFormatMethod) {
            stringLiteral.append("{").append(index).append("}");
        }
        else {
            stringLiteral.append("%s");
        }
    }
}

