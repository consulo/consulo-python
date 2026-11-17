// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
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
import org.jspecify.annotations.Nullable;


// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyDemorganIntention", categories = "Python", fileExtensions = "py")
public final class PyDemorganIntention extends PsiElementBaseIntentionAction {
    public PyDemorganIntention() {
        setText(PyLocalize.intnNameDemorganLaw());
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
        PyElementType op = element.getOperator();
        if (op == PyTokenTypes.AND_KEYWORD || op == PyTokenTypes.OR_KEYWORD) {
            return PyLocalize.intnNameDemorganLaw();
        }
        return null;
    }

    private void invoke(PyBinaryExpression element, Editor editor) {
        PyElementType op = element.getOperator();
        assert op != null;
        String converted = convertConjunctionExpression(element, op);
        replaceExpression(converted, element);
    }

    private static void replaceExpression(String newExpression, PyBinaryExpression expression) {
        PsiElement expressionToReplace = expression;
        String expString = "not(" + newExpression + ')';
        PsiElement parent = expression.getParent().getParent();
        if (isNegation(parent)) {
            expressionToReplace = parent;
            expString = newExpression;
        }
        PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
        PyExpression newCall = generator.createExpressionFromText(LanguageLevel.forElement(expression), expString);
        expressionToReplace.replace(newCall);
        // codeStyleManager = expression.getManager().getCodeStyleManager()
        // TODO codeStyleManager.reformat(insertedElement)
    }

    private static String convertConjunctionExpression(PyBinaryExpression exp, PyElementType tokenType) {
        PyExpression lhs = exp.getLeftExpression();
        String lhsText;
        String rhsText;
        if (isConjunctionExpression(lhs, tokenType)) {
            lhsText = convertConjunctionExpression((PyBinaryExpression)lhs, tokenType);
        }
        else {
            lhsText = convertLeafExpression(lhs);
        }

        PyExpression rhs = exp.getRightExpression();
        if (isConjunctionExpression(rhs, tokenType)) {
            rhsText = convertConjunctionExpression((PyBinaryExpression)rhs, tokenType);
        }
        else {
            rhsText = convertLeafExpression(rhs);
        }

        String flippedConjunction = (tokenType == PyTokenTypes.AND_KEYWORD) ? " or " : " and ";
        return lhsText + flippedConjunction + rhsText;
    }

    private static String convertLeafExpression(@Nullable PyExpression condition) {
        if (condition == null) {
            return "";
        }
        else if (isNegation(condition)) {
            PyExpression negated = getNegated(condition);
            if (negated == null) {
                return "";
            }
            return negated.getText();
        }
        else {
            if (condition instanceof PyBinaryExpression) {
                return "not(" + condition.getText() + ")";
            }
            return "not " + condition.getText();
        }
    }

    private static @Nullable PyExpression getNegated(PyExpression expression) {
        return ((PyPrefixExpression)expression).getOperand();  // TODO strip ()
    }

    private static boolean isConjunctionExpression(@Nullable PyExpression expression, PyElementType tokenType) {
        if (expression instanceof PyBinaryExpression) {
            PyElementType operator = ((PyBinaryExpression)expression).getOperator();
            return operator == tokenType;
        }
        return false;
    }

    private static boolean isNegation(@Nullable PsiElement expression) {
        if (!(expression instanceof PyPrefixExpression)) {
            return false;
        }
        PyElementType op = ((PyPrefixExpression)expression).getOperator();
        return op == PyTokenTypes.NOT_KEYWORD;
    }
}

