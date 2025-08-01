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
package com.jetbrains.python.impl.debugger.settings;

import consulo.configurable.IdeaConfigurableUi;
import consulo.disposer.Disposable;
import consulo.ui.ex.NonEmptyInputValidator;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.table.TableModelEditor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Function;

public class PyDebuggerSteppingConfigurableUi implements IdeaConfigurableUi<PyDebuggerSettings> {
  private static final ColumnInfo[] COLUMNS = {
    new EnabledColumn(),
    new FilterColumn()
  };
  private JPanel myPanel;
  private JPanel mySteppingPanel;
  private JBCheckBox myLibrariesFilterCheckBox;
  private JBCheckBox myStepFilterEnabledCheckBox;
  private TableModelEditor<PySteppingFilter> myPySteppingFilterEditor;

  public PyDebuggerSteppingConfigurableUi() {
    myStepFilterEnabledCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@Nullable ActionEvent e) {
        myPySteppingFilterEditor.enabled(myStepFilterEnabledCheckBox.isSelected());
      }
    });
  }

  private void createUIComponents() {
    TableModelEditor.DialogItemEditor<PySteppingFilter> itemEditor = new DialogEditor();
    myPySteppingFilterEditor =
      new TableModelEditor<>(COLUMNS, itemEditor, "No script filters configured", PySteppingFilter::new);
    mySteppingPanel = new JPanel(new BorderLayout());
    mySteppingPanel.add(myPySteppingFilterEditor.createComponent());
  }

  @Override
  public void reset(@Nonnull PyDebuggerSettings settings) {
    myLibrariesFilterCheckBox.setSelected(settings.isLibrariesFilterEnabled());
    myStepFilterEnabledCheckBox.setSelected(settings.isSteppingFiltersEnabled());
    myPySteppingFilterEditor.reset(settings.getSteppingFilters());
    myPySteppingFilterEditor.enabled(myStepFilterEnabledCheckBox.isSelected());
  }

  @Override
  public boolean isModified(@Nonnull PyDebuggerSettings settings) {
    return myLibrariesFilterCheckBox.isSelected() != settings.isLibrariesFilterEnabled() || myStepFilterEnabledCheckBox.isSelected() != settings
      .isSteppingFiltersEnabled() ||
      myPySteppingFilterEditor.isModified();
  }

  @Override
  public void apply(@Nonnull PyDebuggerSettings settings) {
    settings.setLibrariesFilterEnabled(myLibrariesFilterCheckBox.isSelected());
    settings.setSteppingFiltersEnabled(myStepFilterEnabledCheckBox.isSelected());
    if (myPySteppingFilterEditor.isModified()) {
      settings.setSteppingFilters(myPySteppingFilterEditor.apply());
    }
  }

  @Nonnull
  @Override
  public JComponent getComponent(@Nonnull Disposable parentDisposable) {
    return myPanel;
  }

  private static class EnabledColumn extends TableModelEditor.EditableColumnInfo<PySteppingFilter, Boolean> {
    @Nullable
    @Override
    public Boolean valueOf(PySteppingFilter filter) {
      return filter.isEnabled();
    }

    @Override
    public Class<?> getColumnClass() {
      return Boolean.class;
    }

    @Override
    public void setValue(PySteppingFilter filter, Boolean value) {
      filter.setEnabled(value);
    }
  }

  private static class FilterColumn extends TableModelEditor.EditableColumnInfo<PySteppingFilter, String> {
    @Nullable
    @Override
    public String valueOf(PySteppingFilter filter) {
      return filter.getFilter();
    }

    @Override
    public Class<?> getColumnClass() {
      return String.class;
    }

    @Override
    public void setValue(PySteppingFilter filter, String value) {
      filter.setFilter(value);
    }
  }

  private class DialogEditor implements TableModelEditor.DialogItemEditor<PySteppingFilter> {
    @Override
    public PySteppingFilter clone(@Nonnull PySteppingFilter item, boolean forInPlaceEditing) {
      return new PySteppingFilter(item.isEnabled(), item.getFilter());
    }

    @Nonnull
    @Override
    public Class<PySteppingFilter> getItemClass() {
      return PySteppingFilter.class;
    }

    @Override
    public void edit(@Nonnull PySteppingFilter item, @Nonnull Function<PySteppingFilter, PySteppingFilter> mutator, boolean isAdd) {
      String pattern = Messages.showInputDialog(myPanel,
                                                "Specify glob pattern ('*', '?' and '[seq]' allowed, semicolon ';' as name separator):",
                                                "Stepping Filter",
                                                null,
                                                item.getFilter(),
                                                new
                                                    NonEmptyInputValidator());
      if (pattern != null) {
        mutator.apply(item).setFilter(pattern);
        myPySteppingFilterEditor.getModel().fireTableDataChanged();
      }
    }

    @Override
    public void applyEdited(@Nonnull PySteppingFilter oldItem, @Nonnull PySteppingFilter newItem) {
      oldItem.setFilter(newItem.getFilter());
    }

    @Override
    public boolean isUseDialogToAdd() {
      return true;
    }
  }
}
