/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.run;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.configuration.ui.SettingsEditor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Alexander Koshevoy
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyRunConfigurationEditorExtension {
  ExtensionPointName<PyRunConfigurationEditorExtension> EP_NAME = ExtensionPointName.create(PyRunConfigurationEditorExtension.class);

  boolean accepts(@Nonnull AbstractPythonRunConfiguration configuration);

  @Nonnull
  SettingsEditor<AbstractPythonRunConfiguration> createEditor(@Nonnull AbstractPythonRunConfiguration configuration);

  class Factory {
    @Nullable
    public static PyRunConfigurationEditorExtension getExtension(@Nonnull AbstractPythonRunConfiguration<?> configuration) {
      for (PyRunConfigurationEditorExtension extension : EP_NAME.getExtensionList()) {
        if (extension.accepts(configuration)) {
          return extension;
        }
      }
      return null;
    }
  }
}
