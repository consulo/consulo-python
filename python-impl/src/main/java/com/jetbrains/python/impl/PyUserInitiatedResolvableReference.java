// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Reference that may be resolved differently (i.e use better {@link TypeEvalContext}) when resolved by user request
 * @author Ilya.Kazakevich
 */
@FunctionalInterface
public interface PyUserInitiatedResolvableReference {
  @Nullable
  PsiElement userInitiatedResolve();
}
