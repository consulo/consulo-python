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

package com.jetbrains.python.impl.debugger;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.*;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author traff
 */
@ExtensionImpl
public class PyDebuggerConfigurable implements SearchableConfigurable, Configurable.NoScroll, ProjectConfigurable {
  private final PyDebuggerOptionsProvider mySettings;
  private JPanel myMainPanel;
  private JCheckBox myAttachToSubprocess;
  private JCheckBox mySaveSignatures;
  private JButton myClearCacheButton;
  private JCheckBox mySupportGevent;

  private final Project myProject;

  @Inject
  public PyDebuggerConfigurable(Project project, PyDebuggerOptionsProvider settings) {
    myProject = project;
    mySettings = settings;
  }

  @Nullable
  @Override
  public String getParentId() {
    return StandardConfigurableIds.EXECUTION_GROUP;
  }

  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Python Debugger");
  }

  @Nonnull
  public String getId() {
    return "py.debugger";
  }

  public JComponent createComponent() {
    myClearCacheButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        PySignatureCacheManager.getInstance(myProject).clearCache();
      }
    });
    return myMainPanel;
  }

  public boolean isModified() {
    return myAttachToSubprocess.isSelected() != mySettings.isAttachToSubprocess() ||
           mySaveSignatures.isSelected() != mySettings.isSaveCallSignatures() ||
           mySupportGevent.isSelected() != mySettings.isSupportGeventDebugging();
  }

  public void apply() throws ConfigurationException {
    mySettings.setAttachToSubprocess(myAttachToSubprocess.isSelected());
    mySettings.setSaveCallSignatures(mySaveSignatures.isSelected());
    mySettings.setSupportGeventDebugging(mySupportGevent.isSelected());
  }

  public void reset() {
    myAttachToSubprocess.setSelected(mySettings.isAttachToSubprocess());
    mySaveSignatures.setSelected(mySettings.isSaveCallSignatures());
    mySupportGevent.setSelected(mySettings.isSupportGeventDebugging());
  }

  public void disposeUIResources() {
  }
}
