// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation;

import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Allows you to inject quick info into python documentation provider
 *
 * @author Ilya.Kazakevich
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PythonDocumentationQuickInfoProvider {
    ExtensionPointName<PythonDocumentationQuickInfoProvider> EP_NAME =
        ExtensionPointName.create(PythonDocumentationQuickInfoProvider.class);

    /**
     * Return quick info for <strong>original</strong> element.
     *
     * @param originalElement original element
     * @return info (if exists) or null (if another provider should be checked)
     */
    default @Nullable String getQuickInfo(PsiElement originalElement) {
        return null;
    }

    default @Nullable String getHoverAdditionalQuickInfo(TypeEvalContext context, @Nullable PsiElement originalElement) {
        return null;
    }
}
