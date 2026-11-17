// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyiStubSuppressor {
    ExtensionPointName<PyiStubSuppressor> EP_NAME = ExtensionPointName.create(PyiStubSuppressor.class);

    /**
     * @param file pyi file containing stubs for the corresponding module.
     * @return true if the specified {@code file} should not be used in code insight, false otherwise.
     */
    boolean isIgnoredStub(PyiFile file);

    static boolean isIgnoredStub(@Nullable PsiFile file) {
        if (!(file instanceof PyiFile)) {
            return false;
        }
        return EP_NAME.getExtensionList().stream().anyMatch(it -> it.isIgnoredStub((PyiFile) file));
    }
}
