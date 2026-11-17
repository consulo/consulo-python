// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Reports deprecations that a library declares in its own terms rather than with PEP 702 {@code @deprecated}, for example a
 * model field marked deprecated by a field specifier argument. Consulted for resolved symbols that carry no deprecation
 * message of their own.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyDeprecationProvider {
    /**
     * The deprecation message for {@code element}, or {@code null} if this provider does not consider it deprecated.
     */
    @Nullable
    String getDeprecationMessage(PsiElement element, TypeEvalContext context);

    /**
     * The first deprecation message any registered provider reports for {@code element}.
     */
    static @Nullable String findDeprecationMessage(PsiElement element, TypeEvalContext context) {
        return Application.get().getExtensionPoint(PyDeprecationProvider.class)
            .computeSafeIfAny(it -> it.getDeprecationMessage(element, context));
    }
}
