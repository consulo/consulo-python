// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.language.psi.PsiElement;
import com.jetbrains.python.ast.PyAstTypeParameterListOwner;
import org.jspecify.annotations.Nullable;

/**
 * PSI element that can contain {@link PyTypeParameterList}
 * which was added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeParameterListOwner extends PyAstTypeParameterListOwner, PsiElement {
    @Override
    @Nullable
    PyTypeParameterList getTypeParameterList();
}
