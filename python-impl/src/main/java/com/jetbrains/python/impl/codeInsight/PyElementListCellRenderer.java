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

package com.jetbrains.python.impl.codeInsight;

import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.navigation.ItemPresentation;
import consulo.navigation.NavigationItem;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;

/**
 * @author yole
 */
public class PyElementListCellRenderer extends PsiElementListCellRenderer
{
  public String getElementText(final PsiElement element) {
    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      return name == null ? "" : name;
    }
    return element.getText();
  }

  protected String getContainerText(final PsiElement element, final String name) {
    if (element instanceof NavigationItem) {
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      if (presentation != null) {
        return presentation.getLocationString();
      }
    }
    return null;
  }

  protected int getIconFlags() {
    return 0;
  }
}
