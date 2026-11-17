// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.settings;

import consulo.configurable.IdeaConfigurableUi;
import consulo.disposer.Disposable;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.NonEmptyInputValidator;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.ex.awt.table.TableModelEditor;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

public class PyDebuggerSteppingConfigurableUi implements IdeaConfigurableUi<PyDebuggerSettings> {
    private final TableModelEditor<PySteppingFilter> myPySteppingFilterEditor;
    private final JPanel myPanel;
    private final JBCheckBox myLibrariesFilterCheckBox;
    private final JBCheckBox myStepFilterEnabledCheckBox;
    private final JBCheckBox myAlwaysDoSmartStepIntoCheckBox;

    public PyDebuggerSteppingConfigurableUi() {
        myPySteppingFilterEditor = new TableModelEditor<>(
            new ColumnInfo[]{new EnabledColumn(), new FilterColumn()},
            new DialogEditor(),
            PyLocalize.debuggerSteppingNoScriptFilters().get(),
            PySteppingFilter::new
        );
        // JB builds this panel with the Kotlin UI DSL: three checkbox rows and a resizable row with the filter table
        myAlwaysDoSmartStepIntoCheckBox = new JBCheckBox(PyLocalize.formDebuggerSteppingAlwaysDoSmartStepInto().get());
        myLibrariesFilterCheckBox = new JBCheckBox(PyLocalize.formDebuggerSteppingCheckboxTextDoNotStepIntoLibraryScripts().get());
        myStepFilterEnabledCheckBox = new JBCheckBox(PyLocalize.formDebuggerSteppingDoNotStepIntoScripts().get());

        JPanel rows = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
        rows.add(myAlwaysDoSmartStepIntoCheckBox);
        rows.add(myLibrariesFilterCheckBox);
        rows.add(myStepFilterEnabledCheckBox);

        myPanel = new JPanel(new BorderLayout());
        myPanel.add(rows, BorderLayout.NORTH);
        myPanel.add(myPySteppingFilterEditor.createComponent(), BorderLayout.CENTER);

        // JB: .enabledIf(myStepFilterEnabledCheckBox.selected)
        myStepFilterEnabledCheckBox.addActionListener(e -> myPySteppingFilterEditor.enabled(myStepFilterEnabledCheckBox.isSelected()));
    }

    @Override
    public void reset(PyDebuggerSettings settings) {
        myLibrariesFilterCheckBox.setSelected(settings.isLibrariesFilterEnabled());
        myStepFilterEnabledCheckBox.setSelected(settings.isSteppingFiltersEnabled());
        myAlwaysDoSmartStepIntoCheckBox.setSelected(settings.isAlwaysDoSmartStepInto());
        myPySteppingFilterEditor.reset(settings.getSteppingFilters());
        myPySteppingFilterEditor.enabled(myStepFilterEnabledCheckBox.isSelected());
    }

    @Override
    public boolean isModified(PyDebuggerSettings settings) {
        return myLibrariesFilterCheckBox.isSelected() != settings.isLibrariesFilterEnabled() ||
            myStepFilterEnabledCheckBox.isSelected() != settings.isSteppingFiltersEnabled() ||
            myAlwaysDoSmartStepIntoCheckBox.isSelected() != settings.isAlwaysDoSmartStepInto() ||
            myPySteppingFilterEditor.isModified();
    }

    @Override
    public void apply(PyDebuggerSettings settings) {
        settings.setLibrariesFilterEnabled(myLibrariesFilterCheckBox.isSelected());
        settings.setSteppingFiltersEnabled(myStepFilterEnabledCheckBox.isSelected());
        settings.setAlwaysDoSmartStepIntoEnabled(myAlwaysDoSmartStepIntoCheckBox.isSelected());
        if (myPySteppingFilterEditor.isModified()) {
            settings.setSteppingFilters(myPySteppingFilterEditor.apply());
        }
    }

    @Override
    public JComponent getComponent(Disposable parentDisposable) {
        return myPanel;
    }

    private static class EnabledColumn extends TableModelEditor.EditableColumnInfo<PySteppingFilter, Boolean> {
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
        @Override
        public String valueOf(PySteppingFilter filter) {
            return filter.getFilter();
        }

        @Override
        public void setValue(PySteppingFilter filter, String value) {
            filter.setFilter(value);
        }
    }

    private class DialogEditor implements TableModelEditor.DialogItemEditor<PySteppingFilter> {
        @Override
        public PySteppingFilter clone(PySteppingFilter item, boolean forInPlaceEditing) {
            return new PySteppingFilter(item.isEnabled(), item.getFilter());
        }

        @Override
        public Class<PySteppingFilter> getItemClass() {
            return PySteppingFilter.class;
        }

        @Override
        public void edit(PySteppingFilter item, Function<PySteppingFilter, PySteppingFilter> mutator, boolean isAdd) {
            String pattern = Messages.showInputDialog(
                myPanel,
                PyLocalize.debuggerSteppingFilterSpecifyPattern().get(),
                PyLocalize.debuggerSteppingFilter().get(),
                null,
                item.getFilter(),
                new NonEmptyInputValidator()
            );
            if (pattern != null) {
                mutator.apply(item).setFilter(pattern);
                myPySteppingFilterEditor.getModel().fireTableDataChanged();
            }
        }

        @Override
        public void applyEdited(PySteppingFilter oldItem, PySteppingFilter newItem) {
            oldItem.setFilter(newItem.getFilter());
        }

        @Override
        public boolean isUseDialogToAdd() {
            return true;
        }
    }
}
