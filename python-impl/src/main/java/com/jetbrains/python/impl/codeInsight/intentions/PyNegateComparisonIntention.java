// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.codeInsight.ConditionUtil;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyFile;
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
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyNegateComparisonIntention", categories = "Python", fileExtensions = "py")
public final class PyNegateComparisonIntention extends PsiElementBaseIntentionAction {
    public PyNegateComparisonIntention() {
        setText(PyLocalize.intnNameNegateComparison());
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

    private static @Nullable LocalizeValue getPresentation(PyBinaryExpression element) {
        if (!(element.getContainingFile() instanceof PyFile)) {
            return null;
        }

        Pair<String, String> negation = ConditionUtil.findComparisonNegationOperators(element);
        if (negation != null) {
            return PyLocalize.intnNegateComparison(negation.getFirst(), negation.getSecond());
        }
        return null;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyBinaryExpression element = PsiTreeUtil.getParentOfType(leaf, PyBinaryExpression.class, false);
        if (element == null) {
            return;
        }
        ConditionUtil.negateComparisonExpression(project, element.getContainingFile(), element);
    }
}
