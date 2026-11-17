// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.impl.psi;

import consulo.language.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

public final class PsiElementExtKt {
    private PsiElementExtKt() {
    }

    /**
     * @param classOnly limit ancestors to this class only
     */
    public static <T extends PsiElement> List<T> getAncestors(PsiElement element, Class<? extends T> classOnly) {
        return getAncestors(element, element.getContainingFile(), classOnly);
    }

    /**
     * @param classOnly limit ancestors to this class only
     * @param limit     upper limit to prevent huge unstub. {@link consulo.language.psi.PsiFile} is good choice
     */
    @SuppressWarnings("unchecked")
    public static <T extends PsiElement> List<T> getAncestors(PsiElement element, PsiElement limit, Class<? extends T> classOnly) {
        PsiElement currentElement = element;
        List<T> result = new ArrayList<>();
        while (currentElement != limit) {
            currentElement = currentElement.getParent();
            if (classOnly.isInstance(currentElement)) {
                // Checked one line above
                result.add((T)currentElement);
            }
        }

        return result;
    }
}
