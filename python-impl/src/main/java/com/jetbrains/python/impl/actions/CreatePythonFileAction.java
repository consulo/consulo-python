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

package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PythonFileType;
import consulo.application.dumb.DumbAware;
import consulo.ide.action.CreateFileFromTemplateAction;
import consulo.ide.action.CreateFileFromTemplateDialog;
import consulo.language.psi.PsiDirectory;
import consulo.localize.LocalizeValue;
import consulo.module.extension.ModuleExtension;
import consulo.project.Project;
import consulo.python.module.extension.PyModuleExtension;
import consulo.python.psi.icon.PythonPsiIconGroup;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class CreatePythonFileAction extends CreateFileFromTemplateAction implements DumbAware {
  public CreatePythonFileAction() {
    super(LocalizeValue.localizeTODO("Python File"), LocalizeValue.localizeTODO("Creates a Python file from the specified template"), PythonPsiIconGroup.python());
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(LocalizeValue.localizeTODO("New Python file"))
      .addKind(LocalizeValue.localizeTODO("Python file"), PythonFileType.INSTANCE.getIcon(), "Python Script")
      .addKind(LocalizeValue.localizeTODO("Python unit test"), PythonFileType.INSTANCE.getIcon(), "Python Unit Test");
  }

  @Nonnull
  @Override
  protected LocalizeValue getActionName(PsiDirectory directory, String newName, String templateName) {
    return LocalizeValue.localizeTODO("Create Python script " + newName);
  }

  @Nullable
  @Override
  protected Class<? extends ModuleExtension> getModuleExtensionClass() {
    return PyModuleExtension.class;
  }
}
