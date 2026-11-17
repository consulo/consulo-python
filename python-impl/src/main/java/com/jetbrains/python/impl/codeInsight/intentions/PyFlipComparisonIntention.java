// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Map;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyFlipComparisonIntention", categories = "Python", fileExtensions = "py")
public final class PyFlipComparisonIntention extends PsiElementBaseIntentionAction {
    public PyFlipComparisonIntention() {
        setText(PyLocalize.intnNameFlipComparison());
    }

    private static class Holder {
        private static final Map<PyElementType, String> FLIPPED_OPERATORS = Map.of(
            PyTokenTypes.EQEQ, "==",
            PyTokenTypes.NE, "!=",
            PyTokenTypes.NE_OLD, "<>",
            PyTokenTypes.GE, "<=",
            PyTokenTypes.LE, ">=",
            PyTokenTypes.GT, "<",
            PyTokenTypes.LT, ">");
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PyBinaryExpression element = PsiTreeUtil.getParentOfType(leaf, PyBinaryExpression.class, false);
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

    private static @Nullable LocalizeValue getPresentation(PyBinaryExpression binaryExpression) {
        PyBinaryExpression element = binaryExpression;
        while (element != null) {
            PyElementType operator = element.getOperator();
            if (operator != null && Holder.FLIPPED_OPERATORS.containsKey(operator)) {
                String operatorText = element.getPsiOperator().getText();
                String flippedOperatorText = Holder.FLIPPED_OPERATORS.get(operator);
                if (flippedOperatorText.equals(operatorText)) {
                    return PyLocalize.intnFlipComparison(operatorText);
                }
                else {
                    return PyLocalize.intnFlipComparisonToOperator(operatorText, flippedOperatorText);
                }
            }
            element = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
        }
        return null;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyBinaryExpression element = PsiTreeUtil.getParentOfType(leaf, PyBinaryExpression.class, false);
        while (element != null) {
            PyElementType operator = element.getOperator();
            if (operator != null && Holder.FLIPPED_OPERATORS.containsKey(operator)) {
                PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
                element.replace(elementGenerator.createBinaryExpression(Holder.FLIPPED_OPERATORS.get(operator),
                    element.getRightExpression(),
                    element.getLeftExpression()));
                return;
            }
            element = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
        }
    }
}
