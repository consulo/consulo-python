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
package com.jetbrains.python.impl.refactoring.introduce;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyExpression;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.language.editor.ui.awt.EditorComboBoxEditor;
import consulo.language.editor.ui.awt.EditorComboBoxRenderer;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.StringComboboxEditor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.EnumSet;

/**
 * @author Alexey.Ivanov
 * @since 2009-08-18
 */
public class PyIntroduceDialog extends DialogWrapper {
  private JPanel myContentPane;
  private JLabel myNameLabel;
  private ComboBox myNameComboBox;
  private JCheckBox myReplaceAll;
  private JRadioButton mySamePlace;
  private JRadioButton myConstructor;
  private JRadioButton mySetUp;
  private JPanel myPlaceSelector;

  private final Project myProject;
  private final int myOccurrencesCount;
  private final IntroduceValidator myValidator;
  private final PyExpression myExpression;
  private final String myHelpId;

  public PyIntroduceDialog(Project project,
                           String caption,
                           IntroduceValidator validator,
                           String helpId,
                           IntroduceOperation operation) {
    super(project, true);
    myOccurrencesCount = operation.getOccurrences().size();
    myValidator = validator;
    myProject = project;
    myExpression = operation.getInitializer();
    myHelpId = helpId;
    setUpNameComboBox(operation.getSuggestedNames());

    setTitle(caption);
    init();
    setupDialog(operation.getAvailableInitPlaces());
    updateControls();
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  private void setUpNameComboBox(Collection<String> possibleNames) {
    EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, PythonFileType.INSTANCE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));
    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);

    myNameComboBox.addItemListener(e -> updateControls());

    ((EditorTextField)myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        updateControls();
      }
    });

    myContentPane.registerKeyboardAction(
      e -> myNameComboBox.requestFocus(),
      KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  private void setupDialog(EnumSet<IntroduceHandler.InitPlace> availableInitPlaces) {
    myReplaceAll.setMnemonic(KeyEvent.VK_A);
    myNameLabel.setLabelFor(myNameComboBox);
    myPlaceSelector.setVisible(availableInitPlaces.size() > 1);
    myConstructor.setVisible(availableInitPlaces.contains(IntroduceHandler.InitPlace.CONSTRUCTOR));
    mySetUp.setVisible(availableInitPlaces.contains(IntroduceHandler.InitPlace.SET_UP));
    mySamePlace.setSelected(true);
    
    // Replace occurrences check box setup
    if (myOccurrencesCount > 1) {
      myReplaceAll.setSelected(false);
      myReplaceAll.setEnabled(true);
      myReplaceAll.setText(myReplaceAll.getText() + " (" + myOccurrencesCount + " occurrences)");
    }
    else {
      myReplaceAll.setSelected(false);
      myReplaceAll.setEnabled(false);
    }
  }

  @Override
  @RequiredUIAccess
  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @Nullable
  public String getName() {
    Object item = myNameComboBox.getEditor().getItem();
    if ((item instanceof String) && ((String)item).length() > 0) {
      return ((String)item).trim();
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  public PyExpression getExpression() {
    return myExpression;
  }

  public boolean doReplaceAllOccurrences() {
    return myReplaceAll.isSelected();
  }

  private void updateControls() {
    boolean nameValid = myValidator.isNameValid(getName(), getProject());
    setOKActionEnabled(nameValid);
    setErrorText(!nameValid ? PyLocalize.refactoringIntroduceNameError() : LocalizeValue.empty());
  }

  public IntroduceHandler.InitPlace getInitPlace() {
    if (myConstructor.isSelected()) return IntroduceHandler.InitPlace.CONSTRUCTOR;
    if (mySetUp.isSelected())  return IntroduceHandler.InitPlace.SET_UP;
    return IntroduceHandler.InitPlace.SAME_METHOD;
  }
}
