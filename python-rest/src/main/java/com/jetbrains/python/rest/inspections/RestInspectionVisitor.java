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

package com.jetbrains.python.rest.inspections;

import com.jetbrains.rest.validation.RestElementVisitor;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User : catherine
 */
public abstract class RestInspectionVisitor extends RestElementVisitor {
  @Nullable
  private final ProblemsHolder myHolder;
  public RestInspectionVisitor(@Nullable final ProblemsHolder holder) {
    myHolder = holder;
  }

  public RestInspectionVisitor(@Nullable ProblemsHolder problemsHolder,
                               @Nonnull LocalInspectionToolSession session) {
    myHolder = problemsHolder;
  }

  @Nullable
  protected ProblemsHolder getHolder() {
    return myHolder;
  }

  protected final void registerProblem(final PsiElement element,
                                       final String message){
    if (element == null || element.getTextLength() == 0){
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message);
    }
  }

  protected final void registerProblem(@Nullable final PsiElement element,
                                       @Nonnull final String message,
                                       @Nonnull final LocalQuickFix quickFix){
      if (element == null || element.getTextLength() == 0){
          return;
      }
    if (myHolder != null) {
      myHolder.registerProblem(element, message, quickFix);
    }
  }
}
