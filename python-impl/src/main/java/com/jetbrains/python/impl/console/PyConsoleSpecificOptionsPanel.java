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

package com.jetbrains.python.impl.console;

import consulo.document.Document;
import consulo.codeEditor.EditorEx;
import consulo.module.Module;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.ui.ex.awt.UIUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.impl.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.PyCommonOptionsFormData;
import com.jetbrains.python.impl.run.PyCommonOptionsFormFactory;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author traff
 */
public class PyConsoleSpecificOptionsPanel {
  private final Project myProject;
  private JPanel myWholePanel;
  private JPanel myStartingScriptPanel;
  private JPanel myInterpreterPanel;
  private PyConsoleOptions.PyConsoleSettings myConsoleSettings;
  private EditorTextField myEditorTextField;
  private AbstractPyCommonOptionsForm myCommonOptionsForm;

  public PyConsoleSpecificOptionsPanel(Project project) {
    myProject = project;
  }

  public JPanel createPanel(final PyConsoleOptions.PyConsoleSettings optionsProvider) {
    myInterpreterPanel.setLayout(new BorderLayout());
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(createCommonOptionsFormData());
    myCommonOptionsForm.subscribe();

    myInterpreterPanel.add(myCommonOptionsForm.getMainPanel(),
                           BorderLayout.CENTER);

    configureStartingScriptPanel(optionsProvider);

    return myWholePanel;
  }

  public void apply() {
    myConsoleSettings.myCustomStartScript = myEditorTextField.getText();
    myConsoleSettings.apply(myCommonOptionsForm);
  }

  public boolean isModified() {
    return !myEditorTextField.getText().equals(myConsoleSettings.myCustomStartScript) || myConsoleSettings.isModified(myCommonOptionsForm);
  }

  public void reset() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myEditorTextField.setText(myConsoleSettings.myCustomStartScript);
      }
    });

    myConsoleSettings.reset(myProject, myCommonOptionsForm);
  }

  private PyCommonOptionsFormData createCommonOptionsFormData() {
    return new PyCommonOptionsFormData() {
      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public List<Module> getValidModules() {
        return AbstractPythonRunConfiguration.getValidModules(myProject);
      }

      @Override
      public boolean showConfigureInterpretersLink() {
        return true;
      }
    };
  }

  private void configureStartingScriptPanel(final PyConsoleOptions.PyConsoleSettings optionsProvider) {
    myEditorTextField =
      new EditorTextField(createDocument(myProject, optionsProvider.myCustomStartScript), myProject, PythonFileType.INSTANCE) {
        @Override
        protected EditorEx createEditor() {
          final EditorEx editor = super.createEditor();
          editor.setVerticalScrollbarVisible(true);
          return editor;
        }
      };
    myEditorTextField.setOneLineMode(false);
    myStartingScriptPanel.setLayout(new BorderLayout());
    myStartingScriptPanel.add(myEditorTextField, BorderLayout.CENTER);
    myConsoleSettings = optionsProvider;
  }

  @Nonnull
  public static Document createDocument(@Nonnull final Project project,
                                        @Nonnull String text) {
    text = text.trim();
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "start_script.py", text, true);

    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }
}
