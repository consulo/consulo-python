// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import consulo.application.Application;
import consulo.application.util.function.ThrowableComputable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marker API to indicate places in code analysis where AST access/unstubbing is required,
 * regardless of the result of {@link com.jetbrains.python.psi.types.TypeEvalContext#maySwitchToAST}.
 * <p>
 * Containing files of elements passed to {@link #onFileOf} will be excluded from unstubbing checks in unit tests.
 *
 * @see StubAwareComputation
 */
public final class IntentionalUnstubbing {
    private static final Set<PsiFile> ourForciblyUnstubbed = ConcurrentHashMap.newKeySet();

    private IntentionalUnstubbing() {
    }

    public static <R, E extends Throwable> R onFileOf(PsiElement element, ThrowableComputable<R, E> computable) throws E {
        PsiFile psiFile = element.getContainingFile();
        if (Application.get().isUnitTestMode()) {
            ourForciblyUnstubbed.add(psiFile);
        }
        // We don't rely on AstLoadingFilter behavior for code analysis at the moment, as
        // its disallowTreeLoading is not set up for inspection threads, but
        // calling forceAllowTreeLoading here will ease potential future migration to that API.
        // TODO consulo: AstLoadingFilter.forceAllowTreeLoading(PsiFile, ThrowableComputable) missing (internal in consulo.language.impl)
        return computable.compute();
    }

    public static Set<PsiFile> getForciblyUnstubbedFiles() {
        return Collections.unmodifiableSet(ourForciblyUnstubbed);
    }

    public static void resetForciblyUnstubbedFileSet() {
        ourForciblyUnstubbed.clear();
    }
}
