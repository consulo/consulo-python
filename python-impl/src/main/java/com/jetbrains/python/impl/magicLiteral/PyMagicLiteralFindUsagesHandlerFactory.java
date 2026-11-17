// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.magicLiteral;

import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesHandlerFactory;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Handles find usage for magic literals.
 * <strong>Install it</strong> as {@link FindUsagesHandlerFactory} extension
 * @author Ilya.Kazakevich
 */
@ExtensionImpl
public final class PyMagicLiteralFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    @Override
    public boolean canFindUsages(PsiElement element) {
        return PyMagicLiteralTools.couldBeMagicLiteral(element);
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(PsiElement element, boolean forHighlightUsages) {
        return new MyFindUsagesHandler(element);
    }

    private static class MyFindUsagesHandler extends FindUsagesHandler {
        MyFindUsagesHandler(PsiElement element) {
            super(element);
        }

        @Override
        protected @Nullable Collection<String> getStringsToSearch(PsiElement element) {
            return Collections.singleton(((StringLiteralExpression) element).getStringValue());
        }
    }
}
