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

import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;

/**
 * User: ktisha
 *
 * QuickFix to add parameter to unresolved reference
 */
public class UnresolvedReferenceAddParameterQuickFix implements LocalQuickFix {
  private String myName;
  public UnresolvedReferenceAddParameterQuickFix(String name) {
    myName = name;
  }

  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.add.param.$0", myName);
  }

  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("QFIX.unresolved.reference.add.param");
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyNamedParameter parameter = elementGenerator.createParameter(element.getText() + "=None");
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function != null) {
      final PyParameterList parameterList = function.getParameterList();
      parameterList.addParameter(parameter);
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameterList);
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
      builder.replaceRange(TextRange.create(parameter.getTextLength() - 4, parameter.getTextLength()), "None");
      builder.run();
    }
  }
}
