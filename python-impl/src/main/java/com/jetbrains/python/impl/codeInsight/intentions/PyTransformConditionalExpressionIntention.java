// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
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

/**
 * User: catherine
 * Intention to transform conditional expression into if/else statement
 * For instance,
 *
 * x = a if cond else b
 *
 * into:
 *
 * if cond:
 *    x = a
 * else:
 *    x = b
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyTransformConditionalExpressionIntention", categories = "Python", fileExtensions = "py")
public final class PyTransformConditionalExpressionIntention extends PsiElementBaseIntentionAction {
    public PyTransformConditionalExpressionIntention() {
        setText(PyLocalize.intnTransformIntoIfElseStatement());
    }

    private static @Nullable PyAssignmentStatement findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyAssignmentStatement candidate) {
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
        PyAssignmentStatement element = findElement(leaf);
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
        PyAssignmentStatement element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }



    private void invoke(PyAssignmentStatement element, Editor editor) {
        PyExpression assignedValue = element.getAssignedValue();
        if (assignedValue instanceof PyConditionalExpression expression) {
            PyExpression condition = expression.getCondition();
            PyExpression falsePart = expression.getFalsePart();
            if (condition != null && falsePart != null) {
                String truePartText = expression.getTruePart().getText();
                PyExpression leftHandSideExpression = element.getLeftHandSideExpression();
                if (leftHandSideExpression != null) {
                    String targetText = leftHandSideExpression.getText();
                    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
                    String text = "if " + condition.getText() + ":\n\t" + targetText + " = " + truePartText
                                                            + "\nelse:\n\t" + targetText + " = " + falsePart.getText();
                    PyIfStatement ifStatement =
                            elementGenerator.createFromText(LanguageLevel.forElement(expression), PyIfStatement.class, text);
                    element.replace(ifStatement);
                }
            }
        }
    }

    private @Nullable LocalizeValue getPresentation(PyAssignmentStatement element) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PyFile)) {
            return null;
        }

        if (element.getAssignedValue() instanceof PyConditionalExpression) {
            return PyLocalize.intnTransformIntoIfElseStatement();
        }
        return null;
    }
}

