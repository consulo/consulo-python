// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions.convertToFString;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
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

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertToFStringIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertToFStringIntention extends PsiElementBaseIntentionAction {
    public PyConvertToFStringIntention() {
    }


    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertToFstringLiteral();
    }


    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        if (!(element.getContainingFile() instanceof PyFile) || LanguageLevel.forElement(element.getContainingFile()).isOlderThan(LanguageLevel.PYTHON36)) {
            return false;
        }

        BaseConvertToFStringProcessor processor = findSuitableProcessor(element);
        if (processor != null && processor.isRefactoringAvailable()) {
            return true;
        }
        return false;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        BaseConvertToFStringProcessor processor = findSuitableProcessor(element);
        assert processor != null;
        processor.doRefactoring();
    }

    private static @Nullable BaseConvertToFStringProcessor findSuitableProcessor(PsiElement anchor) {
        PyBinaryExpression binaryExpr = PsiTreeUtil.getParentOfType(anchor, PyBinaryExpression.class);
        if (binaryExpr != null && binaryExpr.getOperator() == PyTokenTypes.PERC) {
            PyStringLiteralExpression pyString = as(binaryExpr.getLeftExpression(), PyStringLiteralExpression.class);
            if (pyString != null) {
                return new OldStyleConvertToFStringProcessor(pyString);
            }
        }

        PyCallExpression callExpr = PsiTreeUtil.getParentOfType(anchor, PyCallExpression.class);
        if (callExpr != null) {
            PyReferenceExpression callee = as(callExpr.getCallee(), PyReferenceExpression.class);
            if (callee != null && PyNames.FORMAT.equals(callee.getName())) {
                PyStringLiteralExpression pyString = as(callee.getQualifier(), PyStringLiteralExpression.class);
                if (pyString != null) {
                    return new NewStyleConvertToFStringProcessor(pyString);
                }
            }
        }
        return null;
    }
}

