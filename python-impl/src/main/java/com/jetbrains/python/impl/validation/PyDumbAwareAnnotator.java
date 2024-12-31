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

package com.jetbrains.python.impl.validation;

import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import consulo.application.dumb.DumbAware;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class PyDumbAwareAnnotator implements Annotator, DumbAware {
  public static ExtensionPointName<PyAnnotator> EP_NAME = ExtensionPointName.create(PyAnnotator.class);

  public PyDumbAwareAnnotator() {
  }

  public void annotate(@Nonnull PsiElement element, @Nonnull AnnotationHolder holder) {
    final PsiFile file = element.getContainingFile();

    for(PyAnnotator annotator: EP_NAME.getExtensionList()) {
      if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(element, holder);
    }
  }
}
