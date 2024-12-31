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

package com.jetbrains.python.impl.testing.pytest;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.project.Project;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awt.PanelWithAnchor;
import consulo.ui.ex.awt.JBLabel;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.PyCommonOptionsFormFactory;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class PyTestConfigurationEditor extends SettingsEditor<PyTestRunConfiguration> implements PanelWithAnchor, PyTestRunConfigurationParams {
  private JPanel myMainPanel;
  private JPanel myCommonOptionsPlaceholder;
  private JTextField myKeywordsTextField;
  private TextFieldWithBrowseButton myTestScriptTextField;
  private JTextField myParamsTextField;
  private JBLabel myTargetLabel;
  private JCheckBox myParametersCheckBox;
  private JCheckBox myKeywordsCheckBox;
  private JPanel myRootPanel;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private final Project myProject;
  private JComponent anchor;

  public PyTestConfigurationEditor(final Project project, PyTestRunConfiguration configuration) {
    myProject = project;
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel());

    String title = PyBundle.message("runcfg.unittest.dlg.select.script.path");
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory
      .createSingleFileOrFolderDescriptor();
    fileChooserDescriptor.setTitle(title);
    myTestScriptTextField.addBrowseFolderListener(title, null, myProject, fileChooserDescriptor);

    myTargetLabel.setLabelFor(myTestScriptTextField);

    myParametersCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myParamsTextField.setEnabled(myParametersCheckBox.isSelected());
      }
    });

    myKeywordsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myKeywordsTextField.setEnabled(myKeywordsCheckBox.isSelected());
      }
    });

    myParametersCheckBox.setSelected(configuration.useParam());
    myKeywordsCheckBox.setSelected(configuration.useKeyword());

    myParamsTextField.setEnabled(configuration.useParam());
    myKeywordsTextField.setEnabled(configuration.useKeyword());

    setAnchor(myCommonOptionsForm.getAnchor());
  }

  protected void resetEditorFrom(PyTestRunConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(s, myCommonOptionsForm);
    myKeywordsTextField.setText(s.getKeywords());
    myTestScriptTextField.setText(s.getTestToRun());
    myKeywordsCheckBox.setSelected(s.useKeyword());
    myParametersCheckBox.setSelected(s.useParam());
    myParamsTextField.setText(s.getParams());
  }

  protected void applyEditorTo(PyTestRunConfiguration s) throws ConfigurationException {
    AbstractPythonRunConfiguration.copyParams(myCommonOptionsForm, s);
    s.setTestToRun(myTestScriptTextField.getText().trim());
    s.setKeywords(myKeywordsTextField.getText().trim());
    s.setParams(myParamsTextField.getText().trim());
    s.useKeyword(myKeywordsCheckBox.isSelected());
    s.useParam(myParametersCheckBox.isSelected());
  }

  @Nonnull
  protected JComponent createEditor() {
    return myRootPanel;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myTargetLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
  }

  @Override
  public boolean useParam() {
    return myParametersCheckBox.isSelected();
  }

  @Override
  public void useParam(boolean useParam) {
    myParametersCheckBox.setSelected(useParam);
  }

  @Override
  public boolean useKeyword() {
    return myKeywordsCheckBox.isSelected();
  }

  @Override
  public void useKeyword(boolean useKeyword) {
    myKeywordsCheckBox.setSelected(useKeyword);
  }
}
