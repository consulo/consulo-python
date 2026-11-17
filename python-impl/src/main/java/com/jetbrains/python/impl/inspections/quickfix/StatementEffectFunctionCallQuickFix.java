// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTupleExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * User: catherine
 * <p>
 * QuickFix to replace statement that has no effect with function call
 */
public class StatementEffectFunctionCallQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixStatementEffect();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyReferenceExpression) {
            String expressionText = element.getText();
            if (PyNames.PRINT.equals(expressionText)) {
                replacePrint(element);
            }
            else if (PyNames.EXEC.equals(expressionText)) {
                replaceExec(element);
            }
            else {
                element.replace(PyElementGenerator.getInstance(project).createCallExpression(LanguageLevel.forElement(element),
                        expressionText));
            }
        }
    }

    private static void replaceExec(PsiElement expression) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(expression.getProject());
        String expressionText = expression.getText();
        StringBuilder stringBuilder = new StringBuilder(expressionText + " (");

        PsiElement next = getNextElement(expression);
        if (next == null) {
            stringBuilder.append(")");
            expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyElement.class,
                    stringBuilder.toString()));
            return;
        }
        String commentText = getComment(next);
        if (next instanceof PyExpressionStatement) {
            PyExpression expr = ((PyExpressionStatement)next).getExpression();
            if (expr instanceof PyBinaryExpression) {
                PsiElement operator = ((PyBinaryExpression)expr).getPsiOperator();
                if (operator instanceof LeafPsiElement && ((LeafPsiElement)operator).getElementType() == PyTokenTypes.IN_KEYWORD) {
                    addInArguments(stringBuilder, (PyBinaryExpression)expr);
                }
                else {
                    stringBuilder.append(next.getText());
                }
            }
            else if (expr instanceof PyTupleExpression) {
                PyExpression[] elements = ((PyTupleExpression)expr).getElements();
                if (elements.length > 1) {
                    if (elements[0] instanceof PyBinaryExpression) {
                        addInArguments(stringBuilder, (PyBinaryExpression)elements[0]);
                    }
                    stringBuilder.append(", ");
                    stringBuilder.append(elements[1].getText());
                }
            }
            else {
                stringBuilder.append(((PyExpressionStatement)next).getExpression().getText());
            }
        }
        else {
            stringBuilder.append(next.getText());
        }
        next.delete();
        stringBuilder.append(")");
        if (commentText != null) {
            stringBuilder.append(commentText);
        }
        expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyStatement.class,
                stringBuilder.toString()));
    }

    private static @Nullable String getComment(@Nullable PsiElement next) {
        String commentText = null;
        if (next != null) {
            PsiElement lastChild = next.getLastChild();
            if (lastChild instanceof PsiComment) {
                commentText = lastChild.getText();
            }
        }
        return commentText;
    }

    private static void addInArguments(StringBuilder stringBuilder, PyBinaryExpression binaryExpression) {
        stringBuilder.append(binaryExpression.getLeftExpression().getText());
        stringBuilder.append(", ");
        PyExpression rightExpression = binaryExpression.getRightExpression();
        if (rightExpression != null) {
            stringBuilder.append(rightExpression.getText());
        }
    }

    private static void replacePrint(PsiElement expression) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(expression.getProject());
        String expressionText = expression.getText();
        StringBuilder stringBuilder = new StringBuilder(expressionText + " (");

        PsiElement next = getNextElement(expression);
        String commentText = getComment(next);
        if (next != null) {
            String text = next instanceof PyExpressionStatement ? ((PyExpressionStatement)next).getExpression().getText() : next.getText();
            stringBuilder.append(text);
            if (text.endsWith(",")) {
                stringBuilder.append(" end=' '");
            }
            next.delete();
        }
        stringBuilder.append(")");
        if (commentText != null) {
            stringBuilder.append(commentText);
        }
        expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyStatement.class,
                stringBuilder.toString()));
    }

    private static PsiElement getNextElement(PsiElement expression) {
        PsiElement whiteSpace = expression.getContainingFile().findElementAt(expression.getTextOffset() + expression.getTextLength());
        PsiElement next = null;
        if (whiteSpace instanceof PsiWhiteSpace) {
            String whiteSpaceText = whiteSpace.getText();
            if (!whiteSpaceText.contains("\n")) {
                next = whiteSpace.getNextSibling();
                while (next instanceof PsiWhiteSpace && whiteSpaceText.contains("\\")) {
                    next = next.getNextSibling();
                }
            }
        }
        else {
            next = whiteSpace;
        }

        RemoveUnnecessaryBackslashQuickFix.removeBackSlash(next);
        if (whiteSpace != null) {
            whiteSpace.delete();
        }
        return next;
    }
}
