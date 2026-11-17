// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * @author Alexei Orischenko
 */
public abstract class BaseReference implements PsiReference, PyUserInitiatedResolvableReference {
  protected final PsiElement myElement;
  private @Nullable TextRange myTextRange;

  protected BaseReference(PsiElement element) {
    myElement = element;
  }

  public BaseReference(PsiElement element, @Nullable TextRange textRange) {
    this(element);
    myTextRange = textRange;
  }

  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  public TextRange getRangeInElement() {
    if (myTextRange == null) {
      return new TextRange(0, myElement.getTextLength());
    }
    return myTextRange;
  }

  @Override
  public String getCanonicalText() {
    return myElement.getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (myElement instanceof PsiNamedElement) {
      return ((PsiNamedElement)myElement).setName(newElementName);
    }
    throw new IncorrectOperationException();
  }

  @Override
  public @Nullable PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return resolve() == element;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public @Nullable PsiElement userInitiatedResolve() {
    // Override this method if your reference may benefit from this knowledge
    return resolve();
  }
}
