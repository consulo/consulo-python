/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

public class PyChangeBaseClassQuickFix implements LocalQuickFix {
  @Nonnull
  @Override
  public String getFamilyName() {
    return PyBundle.message("QFIX.change.base.class");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    assert pyClass != null;

    final PyArgumentList expressionList = pyClass.getSuperClassExpressionList();
    if (expressionList != null && expressionList.getArguments().length != 0) {
      final PyExpression argument = expressionList.getArguments()[0];
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(argument);
      builder.replaceElement(argument, argument.getText());
      final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
      if (virtualFile != null) {
        final Editor editor = FileEditorManager.getInstance(project)
                                               .openTextEditor(OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).build(),
                                                               true);
        assert editor != null;
        builder.run(editor, false);
      }
    }
  }
}
