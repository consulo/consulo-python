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

package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.ConfigurationException;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nullable;
import javax.swing.*;

/**
 * @author yole
 */
@ExtensionImpl
public class PyAutoImportConfigurable implements ApplicationConfigurable {
  private JPanel myMainPanel;
  private JRadioButton myRbFromImport;
  private JRadioButton myRbImport;
  private JCheckBox myShowImportPopupCheckBox;

  @Nonnull
  @Override
  public String getId() {
    return "editor.preferences.import.python";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "editor.preferences.import";
  }

  @RequiredUIAccess
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @RequiredUIAccess
  @Override
  public void reset() {
    PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    myRbFromImport.setSelected(settings.PREFER_FROM_IMPORT);
    myRbImport.setSelected(!settings.PREFER_FROM_IMPORT);
    myShowImportPopupCheckBox.setSelected(settings.SHOW_IMPORT_POPUP);
  }

  @RequiredUIAccess
  @Override
  public boolean isModified() {
    PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    return settings.PREFER_FROM_IMPORT != myRbFromImport.isSelected() || settings.SHOW_IMPORT_POPUP != myShowImportPopupCheckBox.isSelected();
  }

  @RequiredUIAccess
  @Override
  public void apply() throws ConfigurationException {
    PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    settings.PREFER_FROM_IMPORT = myRbFromImport.isSelected();
    settings.SHOW_IMPORT_POPUP = myShowImportPopupCheckBox.isSelected();
  }

  @Nls
  @Override
  public LocalizeValue getDisplayName() {
    return PyLocalize.python();
  }
}
