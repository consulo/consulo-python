// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyResolveResultRater {
    ExtensionPointName<PyResolveResultRater> EP_NAME = ExtensionPointName.create(PyResolveResultRater.class);

    int getImportElementRate(PsiElement target);

    int getMemberRate(PsiElement member, PyType type, TypeEvalContext context);
}
