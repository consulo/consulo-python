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

import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.usage.UsageViewLongNameLocation;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyElementDescriptionProvider implements ElementDescriptionProvider
{
  public String getElementDescription(@Nonnull PsiElement element, @Nonnull ElementDescriptionLocation location) {
    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiNamedElement && element instanceof PyElement) {
        return ((PsiNamedElement)element).getName();
      }
    }
    return null;
  }
}
