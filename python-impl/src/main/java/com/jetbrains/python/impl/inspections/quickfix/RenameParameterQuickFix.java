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

package com.jetbrains.python.impl.inspections.quickfix;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyNamedParameter;

/**
 * Parameter renaming.
* User: dcheryasov
* Date: Nov 30, 2008 6:10:13 AM
*/
public class RenameParameterQuickFix implements LocalQuickFix {
  private final String myNewName;

  public RenameParameterQuickFix(String newName) {
    myNewName = newName;
  }

  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    final PsiElement elt = descriptor.getPsiElement();
    if (elt != null && elt instanceof PyNamedParameter && elt.isWritable()) {
      new RenameProcessor(project, elt, myNewName, false, true).run();
    }
  }

  @Nonnull
  public String getFamilyName() {
    return "Rename parameter";
  }

  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.rename.parameter.to.$0", myNewName);
  }
}
