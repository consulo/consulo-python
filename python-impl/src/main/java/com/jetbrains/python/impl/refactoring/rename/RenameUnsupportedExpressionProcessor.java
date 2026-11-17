// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.refactoring.rename;

import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.usage.UsageInfo;
import org.jspecify.annotations.Nullable;

// Consulo: JB registers "pyvar" as "last"; ordering before it keeps this processor ahead of the generic variable one
@ExtensionImpl(id = "pyUnsupportedExpression", order = "before pyvar")
public final class RenameUnsupportedExpressionProcessor extends RenamePyElementProcessor {
    @Override
    public boolean canProcessElement(PsiElement element) {
        return PsiTreeUtil.instanceOf(
            element,
            PyStringLiteralExpression.class,
            PyNumericLiteralExpression.class,
            PyListLiteralExpression.class,
            PyDictLiteralExpression.class,
            PySetLiteralExpression.class,
            PyStarArgument.class,
            PyCallExpression.class,
            PyBinaryExpression.class,
            PySubscriptionExpression.class
        );
    }

    @Override
    @RequiredWriteAction
    public void renameElement(PsiElement element, String newName, UsageInfo[] usages, @Nullable RefactoringElementListener listener)
        throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public boolean isToSearchInComments(PsiElement element) {
        return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
    }

    @Override
    public void setToSearchInComments(PsiElement element, boolean enabled) {
        PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
    }

    @Override
    public boolean isToSearchForTextOccurrences(PsiElement element) {
        return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE;
    }

    @Override
    public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
        PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE = enabled;
    }
}
