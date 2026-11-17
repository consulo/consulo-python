// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

/**
 * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyCanonicalPathProvider {
    ExtensionPointName<PyCanonicalPathProvider> EP_NAME = ExtensionPointName.create(PyCanonicalPathProvider.class);

    /**
     * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
     *
     * @param symbol   the symbol being imported
     * @param qName    the real qualified name of the symbol being imported.
     * @param foothold the location where the symbol is being imported.
     * @return the qualified name to use in the import statement, or null if no replacement is necessary.
     */
    @Nullable
    QualifiedName getCanonicalPath(@Nullable PsiElement symbol, QualifiedName qName, @Nullable PsiElement foothold);
}
