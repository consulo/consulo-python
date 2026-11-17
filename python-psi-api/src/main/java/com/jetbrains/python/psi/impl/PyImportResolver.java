// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;


@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyImportResolver {
    ExtensionPointName<PyImportResolver> EP_NAME = ExtensionPointName.create(PyImportResolver.class);

    @Nullable
    PsiElement resolveImportReference(QualifiedName name, PyQualifiedNameResolveContext context, boolean withRoots);
}
