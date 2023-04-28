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

package com.jetbrains.python.documentation.doctest;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.HighlightErrorFilter;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import javax.annotation.Nonnull;

/**
 * User : ktisha
 *
 * Do not highlight syntax errors in doctests
 */
@ExtensionImpl
public class PyDocstringErrorFilter extends HighlightErrorFilter {

  public boolean shouldHighlightErrorElement(@Nonnull final PsiErrorElement element) {
    final PsiFile file = element.getContainingFile();
    return !(file instanceof PyDocstringFile);
  }
}
