// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class ImportedResolveResult extends RatedResolveResult {
  private final @Nullable PyImportedNameDefiner myDefiner;

  public ImportedResolveResult(@Nullable PsiElement element, int rate, @Nullable PyImportedNameDefiner definer) {
    super(rate, element);
    myDefiner = definer;
  }

  public @Nullable PyImportedNameDefiner getDefiner() {
    return myDefiner;
  }

  @Override
  public RatedResolveResult replace(PsiElement what) {
    return new ImportedResolveResult(what, getRate(), myDefiner);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ImportedResolveResult result = (ImportedResolveResult)o;
    return Objects.equals(myDefiner, result.myDefiner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myDefiner);
  }
}
