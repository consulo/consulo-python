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

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 19.02.2010
 * Time: 18:50:24
 */
public class ReplaceBuiltinsQuickFix implements LocalQuickFix {
  @Nonnull
  @Override
  public String getName() {
    return PyBundle.message("INTN.convert.builtin.import");
  }

  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.builtin");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiElement importStatement = descriptor.getPsiElement();
    if (importStatement instanceof PyImportStatement) {
      for (PyImportElement importElement : ((PyImportStatement)importStatement).getImportElements()) {
        PyReferenceExpression importReference = importElement.getImportReferenceExpression();
        if (importReference != null) {
          if ("__builtin__".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createFromText(LanguageLevel.getDefault(), PyReferenceExpression.class, "builtins"));
          }
          if ("builtins".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createFromText(LanguageLevel.getDefault(), PyReferenceExpression.class, "__builtin__"));
          }
        }
      }
    }
  }
}
