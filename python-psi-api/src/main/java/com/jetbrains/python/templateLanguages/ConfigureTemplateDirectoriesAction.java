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
package com.jetbrains.python.templateLanguages;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
* @author yole
*/
public class ConfigureTemplateDirectoriesAction implements LocalQuickFix, LowPriorityAction {
  @Nonnull
  @Override
  public String getName() {
    return "Configure template directories";
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Template Languages");
      }
    }, Application.get().getNoneModalityState());
  }
}
