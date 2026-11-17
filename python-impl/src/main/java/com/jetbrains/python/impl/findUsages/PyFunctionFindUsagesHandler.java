// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
public class PyFunctionFindUsagesHandler extends PyFindUsagesHandler {
    private final @Nullable List<PsiElement> myAllElements;

    public PyFunctionFindUsagesHandler(PsiElement psiElement) {
        super(psiElement);
        myAllElements = null;
    }

    public PyFunctionFindUsagesHandler(PsiElement psiElement, List<PsiElement> allElements) {
        super(psiElement);
        myAllElements = allElements;
    }

    @Override
    protected boolean isSearchForTextOccurrencesAvailable(PsiElement psiElement, boolean isSingleFile) {
        return true;
    }

    @Override
    public PsiElement[] getPrimaryElements() {
        List<PsiElement> result = new ArrayList<>();
        if (myAllElements != null) {
            result.addAll(myAllElements);
        }
        else {
            result.add(getPsiElement());
        }

        completePrimaryElementsWithStubAndOriginalElements(result);

        return result.toArray(PsiElement.EMPTY_ARRAY);
    }
}
