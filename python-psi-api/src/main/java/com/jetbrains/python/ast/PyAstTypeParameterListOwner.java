// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast;

import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * PSI element that can contain {@link PyAstTypeParameterList}
 * which was added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyAstTypeParameterListOwner extends PsiElement {

    @Nullable PyAstTypeParameterList getTypeParameterList();
}
