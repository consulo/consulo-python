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

package com.jetbrains.python.rest.run.sphinx;

import com.jetbrains.rest.RestFile;
import com.jetbrains.python.rest.run.RestRunConfiguration;
import com.jetbrains.python.rest.run.RestRunConfigurationType;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.execution.action.RuntimeConfigurationProducer;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * User : catherine
 */
@ExtensionImpl
public class SphinxConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiDirectory mySourceFile = null;

  public SphinxConfigurationProducer() {
    super(RestRunConfigurationType.getInstance().SPHINX_FACTORY);
  }

  public PsiElement getSourceElement() {
    return mySourceFile;
  }

  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiDirectory)) return null;

    mySourceFile = (PsiDirectory)element;
    boolean hasRstFile = false;
    boolean hasConf = false;
    for (PsiFile file : mySourceFile.getFiles()) {
      if ("conf.py".equals(file.getName()))
        hasConf = true;
      if (file instanceof RestFile) {
        hasRstFile = true;
      }
    }
    if (!hasRstFile || !hasConf) return null;
    final Project project = mySourceFile.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    SphinxRunConfiguration configuration = (SphinxRunConfiguration) settings.getConfiguration();
    final VirtualFile vFile = mySourceFile.getVirtualFile();
    configuration.setInputFile(vFile.getPath());

    configuration.setName(((PsiDirectory)element).getName());
    if (configuration.getTask().isEmpty())
      configuration.setTask("html");
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    configuration.setName(configuration.suggestedName());
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    return settings;
  }

  @Nullable
  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @Nonnull List<RunnerAndConfigurationSettings> existingConfigurations,
                                                                 ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiDirectory)) return null;
    final VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
    String path = vFile.getPath();
    for (RunnerAndConfigurationSettings configuration : existingConfigurations) {
      final String scriptName = ((RestRunConfiguration)configuration.getConfiguration()).getInputFile();
      if (FileUtil.toSystemIndependentName(scriptName).equals(FileUtil.toSystemIndependentName(path))) {
        return configuration;
      }
    }
    return null;
  }

  public int compareTo(final RuntimeConfigurationProducer o) {
    return PREFERED;
  }
}
