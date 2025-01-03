/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python.impl.findUsages;

import consulo.find.FindUsagesHandler;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author yole
 */
public class PyFunctionFindUsagesHandler extends FindUsagesHandler
{
  private final List<PsiElement> myAllElements;

  protected PyFunctionFindUsagesHandler(@Nonnull PsiElement psiElement) {
    super(psiElement);
    myAllElements = null;
  }

  protected PyFunctionFindUsagesHandler(@Nonnull PsiElement psiElement, List<PsiElement> allElements) {
    super(psiElement);
    myAllElements = allElements;
  }

  @Override
  protected boolean isSearchForTextOccurencesAvailable(@Nonnull PsiElement psiElement, boolean isSingleFile) {
    return true;
  }

  @Nonnull
  @Override
  public PsiElement[] getPrimaryElements() {
    return myAllElements != null ? myAllElements.toArray(new PsiElement[myAllElements.size()]) : super.getPrimaryElements();
  }
}
