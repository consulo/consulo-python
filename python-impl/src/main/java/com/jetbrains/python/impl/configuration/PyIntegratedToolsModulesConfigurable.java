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

package com.jetbrains.python.impl.configuration;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.NonDefaultProjectConfigurable;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.module.Module;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;

/**
 * @author vlan
 * <p>
 * TODO [VISTALL] move it to python module extension settings
 */
@ExtensionImpl
public class PyIntegratedToolsModulesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable, ProjectConfigurable {
  @Inject
  public PyIntegratedToolsModulesConfigurable(@Nonnull Project project) {
    super(project, "Python Integrated Tools", "reference-python-integrated-tools");
  }

  @Nonnull
  @Override
  public String getId() {
    return "py.integrated.tools";
  }

  @Nullable
  @Override
  public String getParentId() {
    return StandardConfigurableIds.EXECUTION_GROUP;
  }

  @Nonnull
  @Override
  protected Configurable createModuleConfigurable(@Nonnull Module module) {
    return new PyIntegratedToolsConfigurable(module);
  }
}
