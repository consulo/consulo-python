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

package com.jetbrains.python.impl.codeInsight;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.BeanConfigurable;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PySpecificSmartKeysOptions extends BeanConfigurable<PyCodeInsightSettings> implements ApplicationConfigurable {
  public PySpecificSmartKeysOptions() {
    super(PyCodeInsightSettings.getInstance());
    checkBox("INSERT_BACKSLASH_ON_WRAP", "Insert backslash when pressing Enter inside a statement");
    checkBox("INSERT_SELF_FOR_METHODS", "Insert 'self' when defining a method");
    checkBox("INSERT_TYPE_DOCSTUB", "Insert 'type' and 'rtype' to the documentation comment stub");
  }

  @Nonnull
  @Override
  public String getId() {
    return "editor.preferences.smartKeys.python";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "editor.preferences.smartKeys";
  }

  @Override
  public LocalizeValue getDisplayName() {
    return PyLocalize.python();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }
}
