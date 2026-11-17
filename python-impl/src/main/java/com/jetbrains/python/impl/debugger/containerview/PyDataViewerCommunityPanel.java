// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameListener;
import com.jetbrains.python.impl.debugger.array.AbstractDataViewTable;
import com.jetbrains.python.impl.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.impl.debugger.array.JBTableWithRowHeaders;
import consulo.application.Application;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public class PyDataViewerCommunityPanel extends PyDataViewerAbstractPanel {
    public static final Key<AbstractDataViewTable> PY_DATA_VIEWER_COMMUNITY_PANEL_KEY = Key.create("PY_DATA_VIEWER_COMMUNITY_PANEL_KEY");

    private @Nullable AbstractDataViewTable myTable;

    private final JPanel myPanelWithTable = new JPanel(new BorderLayout());

    private final EditorTextField myFormatTextField = createEditorField(DataViewerCommandSource.FORMATTING);

    private final EditorTextField mySlicingTextField = createEditorField(DataViewerCommandSource.SLICING);

    private @Nullable JPanel myTopToolbar;

    // JB: errorLabel is a Kotlin UI DSL text("") cell (JEditorPane)
    private final JBLabel myErrorLabel;

    public PyDataViewerCommunityPanel(PyDataViewerModel dataViewerModel) {
        super(dataViewerModel, false);

        new PyDataViewerCompletionProvider(dataViewerModel.getFrameAccessor()).apply(mySlicingTextField);
        myFormatTextField.setText(dataViewerModel.getFormat());

        add(myPanelWithTable, BorderLayout.CENTER);

        // JB builds this with the Kotlin UI DSL:
        // row { cell(slicingTextField).align(AlignX.FILL).resizableColumn(); label(format); cell(formatTextField) }
        // row { errorLabel = text("") (red) }
        JPanel formatPanel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, 0));
        formatPanel.add(new JBLabel(PyLocalize.formDataViewerFormat().get()), BorderLayout.WEST);
        formatPanel.add(myFormatTextField, BorderLayout.CENTER);

        JPanel slicingRow = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, 0));
        slicingRow.add(mySlicingTextField, BorderLayout.CENTER);
        slicingRow.add(formatPanel, BorderLayout.EAST);

        myErrorLabel = new JBLabel("");
        myErrorLabel.setForeground(JBColor.RED);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, UIUtil.DEFAULT_VGAP));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(UIUtil.DEFAULT_VGAP, 0, 0, 0));
        bottomPanel.add(slicingRow, BorderLayout.NORTH);
        bottomPanel.add(myErrorLabel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setupChangeListener();

        myTopToolbar = createAndSetupTopToolbar();
    }

    @Override
    public String getFormatValueFromUI() {
        String format = myFormatTextField.getText();
        return format.isEmpty() ? "%s" : format;
    }

    @Override
    public void setFormatValueFromUI(String value) {
        myFormatTextField.setText(value);
    }

    @Override
    protected String getSlicingValueFromUI() {
        String slicing = mySlicingTextField.getText();
        return slicing.isEmpty() ? "None" : slicing;
    }

    @Override
    protected void setSlicingValueFromUI(String value) {
        mySlicingTextField.setText(value);
    }

    @Override
    public boolean isColoredValueFromUI() {
        return getDataViewerModel().getProtectedColored();
    }

    @Override
    public void setColoredValueFromUI(boolean value) {
        getDataViewerModel().setProtectedColored(value);
        AbstractDataViewTable table = myTable;
        if (table != null && !table.isEmpty()) {
            ((ColoredCellRenderer) table.getDefaultRenderer(table.getColumnClass(0))).setColored(value);
            table.repaint();
        }
    }

    @Override
    public EditorTextField getSlicingTextField() {
        return mySlicingTextField;
    }

    @Override
    public @Nullable JPanel getTopToolbar() {
        return myTopToolbar;
    }

    @Override
    public void setTopToolbar(@Nullable JPanel topToolbar) {
        myTopToolbar = topToolbar;
    }

    private @Nullable AsyncArrayTableModel getModel() {
        AbstractDataViewTable table = myTable;
        return table != null && table.getModel() instanceof AsyncArrayTableModel model ? model : null;
    }

    @Override
    public void recreateTable() {
        myPanelWithTable.removeAll();
        myPanelWithTable.add(createTable().getScrollPane(), BorderLayout.CENTER);
    }

    @Override
    protected void setupDataProvider() {
        PyDataViewerPanel.addDataProvider(this, sink -> sink.set(PY_DATA_VIEWER_COMMUNITY_PANEL_KEY, myTable));
    }

    private JPanel createAndSetupTopToolbar() {
        ActionManager actionManager = ActionManager.getInstance();

        /* Create the left toolbar */
        DefaultActionGroup leftActionGroup = new DefaultActionGroup();
        AnAction coloringAction = actionManager.getAction("ToggleDataViewColoring");
        if (coloringAction != null) {
            leftActionGroup.add(coloringAction);
        }
        ActionToolbar leftActionToolbar = actionManager.createActionToolbar("PyDataView", leftActionGroup, true);
        leftActionToolbar.setTargetComponent(myPanelWithTable);

        /* Create the right toolbar */
        DefaultActionGroup rightActionGroup = new DefaultActionGroup();
        for (String actionId : List.of("OpenInEditorAction", "ExportTableAction", "SwitchBetweenTableModesAction")) {
            AnAction action = actionManager.getAction(actionId);
            if (action != null) {
                rightActionGroup.add(action);
            }
        }

        ActionToolbar rightActionToolbar = actionManager.createActionToolbar("PyDataView", rightActionGroup, true);
        // For removing the empty space on the right of the toolbar.
        rightActionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
        rightActionToolbar.setTargetComponent(myPanelWithTable);

        // JB: TwoSideComponent(left, right); Consulo has no TwoSideComponent, so a BorderLayout panel with the left toolbar
        // at WEST and the right one at EAST is used
        JPanel twoSideComponent = new JPanel(new BorderLayout());
        twoSideComponent.add(leftActionToolbar.getComponent(), BorderLayout.WEST);
        twoSideComponent.add(rightActionToolbar.getComponent(), BorderLayout.EAST);
        add(twoSideComponent, BorderLayout.BEFORE_FIRST_LINE);

        myTopToolbar = twoSideComponent;

        return twoSideComponent;
    }

    private boolean isVariablePresentInStack() {
        XValueChildrenList values;
        try {
            values = getDataViewerModel().getFrameAccessor().loadFrame(null);
        }
        catch (PyDebuggerException e) {
            throw new RuntimeException(e);
        }
        if (values == null) {
            return true;
        }
        for (int i = 0; i < values.size(); i++) {
            if (values.getValue(i).equals(getDataViewerModel().getDebugValue())) {
                return true;
            }
        }
        return false;
    }

    private void setupChangeListener() {
        getDataViewerModel().getFrameAccessor().addFrameListener(new PyFrameListener() {
            @Override
            public void frameChanged() {
                if (getDataViewerModel().getDebugValue() == null) {
                    return;
                }
                Application.get().executeOnPooledThread(() -> {
                    // Could be that in changed frames our value is missing. (PY-66235)
                    if (isVariablePresentInStack()) {
                        updateModel();
                    }
                });
            }
        });
    }

    private void updateModel() {
        AsyncArrayTableModel model = getModel();
        if (model == null) {
            return;
        }
        model.invalidateCache();
        if (getDataViewerModel().isModified()) {
            apply(getDataViewerModel().getModifiedVarName(), true);
        }
        else {
            updateDebugValue(model);
            Application.get().invokeLater(() -> {
                if (isShowing()) {
                    model.fireTableDataChanged();
                }
            });
        }
    }

    private void updateDebugValue(AsyncArrayTableModel model) {
        PyDebugValue oldValue = model.getDebugValue();
        if (oldValue != null && !oldValue.isTemporary() || getSlicingValueFromUI().isEmpty()) {
            return;
        }
        PyDebugValue newValue = getDebugValue(getSlicingValueFromUI(), false);
        if (newValue != null) {
            model.setDebugValue(newValue);
        }
    }

    @Override
    public AbstractDataViewTable createTable(@Nullable PyDebugValue originalDebugValue, @Nullable ArrayChunk chunk) {
        JBTableWithRowHeaders mainTable = new JBTableWithRowHeaders(PyDataView.isAutoResizeEnabled(getDataViewerModel().getProject()));
        mainTable.getScrollPane().setBorder(BorderFactory.createEmptyBorder());

        myPanelWithTable.add(mainTable.getScrollPane(), BorderLayout.CENTER);
        myPanelWithTable.revalidate();
        myPanelWithTable.repaint();

        myTable = mainTable;
        return mainTable;
    }

    @Override
    public AbstractDataViewTable createTable() {
        return createTable(null, null);
    }

    private EditorTextField createEditorField(DataViewerCommandSource commandSource) {
        return new EditorTextField(EditorFactory.getInstance().createDocument(""),
            getDataViewerModel().getProject(), PythonFileType.INSTANCE, false, true) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                editor.getSettings().setAdditionalColumnsCount(5);
                editor.getContentComponent().addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            onEnterPressed(commandSource);
                        }
                    }
                });
                return editor;
            }
        };
    }

    @Override
    protected void updateUI(ArrayChunk chunk, PyDebugValue originalDebugValue, DataViewStrategy strategy, boolean modifier) {
        myErrorLabel.setVisible(false);
        PyDebugValue debugValue = chunk.getValue();
        AsyncArrayTableModel model = strategy.createTableModel(chunk.getRows(), chunk.getColumns(), this, debugValue);
        model.addToCache(chunk);
        UIUtil.invokeLaterIfNeeded(() -> {
            AbstractDataViewTable table = myTable != null ? myTable : createTable();
            table.setModel(model, modifier);

            updateTabNameSlicingFieldAndFormatField(chunk, originalDebugValue, modifier);

            ColoredCellRenderer cellRenderer = strategy.createCellRenderer(Double.MIN_VALUE, Double.MAX_VALUE, chunk);
            cellRenderer.setColored(getDataViewerModel().getProtectedColored());
            model.fireTableDataChanged();
            model.fireTableCellUpdated(0, 0);
            if (table.getColumnCount() > 0) {
                table.setDefaultRenderer(table.getColumnClass(0), cellRenderer);
            }
            table.setShowColumns(strategy.showColumnHeader());
        });
    }

    public void resize(boolean autoResize) {
        if (myTable != null) {
            myTable.setAutoResize(autoResize);
        }
        apply(getSlicingValueFromUI(), false);
    }

    @Override
    protected void setError(String text, boolean modifier) {
        myErrorLabel.setVisible(true);
        myErrorLabel.setText(composeErrorMessage(text, modifier));
        if (!modifier) {
            if (myTable != null) {
                myTable.setEmpty();
            }
            for (OnNameChangedListener listener : listeners) {
                listener.onNameChanged(PyLocalize.debuggerDataViewEmptyTab().get());
            }
        }
    }
}
