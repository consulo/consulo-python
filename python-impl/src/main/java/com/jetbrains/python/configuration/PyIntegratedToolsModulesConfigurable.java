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

package com.jetbrains.python.configuration;

import consulo.configurable.Configurable;
import consulo.configurable.NonDefaultProjectConfigurable;
import consulo.module.Module;
import consulo.project.Project;

import javax.annotation.Nonnull;

/**
 * @author vlan
 *
 * TODO [VISTALL] move it to python module extension settings
 */
public class PyIntegratedToolsModulesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable {
  public PyIntegratedToolsModulesConfigurable(@Nonnull Project project) {
    super(project, "Python Integrated Tools", "reference-python-integrated-tools");
  }

  @Nonnull
  @Override
  protected Configurable createModuleConfigurable(@Nonnull Module module) {
    return new PyIntegratedToolsConfigurable(module);
  }
}
