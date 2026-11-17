// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.magicLiteral;

import com.google.common.base.Preconditions;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.usage.UsageInfo;
import org.jspecify.annotations.Nullable;

/**
 * Supports renaming for magic literals.
 * <strong>Install it</strong> as {@link RenamePsiElementProcessor} extension
 *
 * @author Ilya.Kazakevich
 */
@ExtensionImpl(order = "before pyvar")
public final class PyMagicLiteralRenameProcessor extends RenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(PsiElement element) {
        return (PyMagicLiteralTools.couldBeMagicLiteral(element));
    }

    @Override
    @RequiredWriteAction
    public void renameElement(PsiElement element,
                              String newName,
                              UsageInfo[] usages,
                              @Nullable RefactoringElementListener listener) {
        Preconditions.checkArgument(canProcessElement(element), "Element can't be renamed, call #canProcessElement first " + element);
        element.replace(PyElementGenerator.getInstance(element.getProject()).createStringLiteral((PyStringLiteralExpression) element, newName));
        for (UsageInfo usage : usages) {
            PsiReference reference = usage.getReference();
            if (reference != null) {
                reference.handleElementRename(newName);
            }
        }
    }
}
