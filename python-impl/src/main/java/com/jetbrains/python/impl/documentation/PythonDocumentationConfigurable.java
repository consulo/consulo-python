// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.google.common.collect.ImmutableMap;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.Component;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.AddEditRemovePanel;
import consulo.ui.ex.awt.BorderLayoutPanel;
import consulo.ui.ex.awt.ColoredTableCellRenderer;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import org.jspecify.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import java.util.ArrayList;
import java.util.Map;

@ExtensionImpl
public final class PythonDocumentationConfigurable implements SearchableConfigurable, Configurable.NoScroll, ApplicationConfigurable {
    private final PythonDocumentationPanel myPanel = new PythonDocumentationPanel();

    @Override
    public String getId() {
        return PythonDocumentationProvider.DOCUMENTATION_CONFIGURABLE_ID;
    }

    // Consulo: JB registers it in the "python.group.settings" group, which Consulo lacks; it is placed into the editor group
    @Override
    public @Nullable String getParentId() {
        return StandardConfigurableIds.EDITOR_GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        // Consulo: JB key "external.documentation.configurable.name" is shown inside the Python group; outside of it the title is used
        return PyCodeInsightLocalize.externalDocumentationTitle();
    }

    @Override
    public String getHelpTopic() {
        return "preferences.ExternalDocumentation";
    }

    @Override
    public Component createUIComponent(Disposable parentDisposable) {
        Border border = IdeBorderFactory.createEmptyBorder(JBUI.insets(10, 0));
        JPanel panelWithDescription =
            JBUI.Panels.simplePanel(new JBLabel(PyCodeInsightLocalize.externalDocumentationDescription().get())).withBorder(border);

        SwingUtilities.updateComponentTreeUI(myPanel); // TODO: create Swing components in this method (see javadoc)
        myPanel.getTable().setShowGrid(false);

        BorderLayoutPanel myComponent = new BorderLayoutPanel()
            .addToTop(panelWithDescription)
            .addToCenter(myPanel);

        return TargetAWT.wrap(myComponent);
    }

    @Override
    public void reset() {
        myPanel.getData().clear();
        myPanel.getData().addAll(PythonDocumentationMap.getInstance().getEntries().entrySet());
    }

    @Override
    public boolean isModified() {
        Map<String, String> originalEntries = Map.copyOf(PythonDocumentationMap.getInstance().getEntries());
        Map<String, String> editedEntries = ImmutableMap.copyOf(myPanel.getData());
        return !editedEntries.equals(originalEntries);
    }

    @Override
    public void apply() throws ConfigurationException {
        PythonDocumentationMap.getInstance().setEntries(ImmutableMap.copyOf(myPanel.getData()));
    }

    private static class PythonDocumentationTableModel extends AddEditRemovePanel.TableModel<Map.Entry<String, String>> {
        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int columnIndex) {
            return columnIndex == 0
                ? PyCodeInsightLocalize.externalDocumentationColumnNameModule().get()
                : PyCodeInsightLocalize.externalDocumentationColumnNameUrlPathPattern().get();
        }

        @Override
        public Object getField(Map.Entry<String, String> o, int columnIndex) {
            return columnIndex == 0 ? o.getKey() : o.getValue();
        }
    }

    private static final PythonDocumentationTableModel ourModel = new PythonDocumentationTableModel();

    private static class PythonDocumentationPanel extends AddEditRemovePanel<Map.Entry<String, String>> {
        PythonDocumentationPanel() {
            super(ourModel, new ArrayList<>());
            setRenderer(1, new ColoredTableCellRenderer() {
                @Override
                protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
                    String text = value == null ? "" : (String) value;
                    int pos = 0;
                    while (pos < text.length()) {
                        int openBrace = text.indexOf('{', pos);
                        if (openBrace == -1) {
                            openBrace = text.length();
                        }
                        append(text.substring(pos, openBrace));
                        int closeBrace = text.indexOf('}', openBrace);
                        if (closeBrace == -1) {
                            closeBrace = text.length();
                        }
                        else {
                            closeBrace++;
                        }
                        append(text.substring(openBrace, closeBrace), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.BLUE.darker()));
                        pos = closeBrace;
                    }
                }
            });
        }

        @Override
        protected Map.Entry<String, String> addItem() {
            return showEditor(null);
        }

        private Map.@Nullable Entry<String, String> showEditor(Map.@Nullable Entry<String, String> entry) {
            PythonDocumentationEntryEditor editor = new PythonDocumentationEntryEditor(this);
            if (entry != null) {
                editor.setEntry(entry);
            }
            editor.show();
            if (editor.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
                return null;
            }
            return editor.getEntry();
        }

        @Override
        protected boolean removeItem(Map.Entry<String, String> o) {
            return true;
        }

        @Override
        protected Map.Entry<String, String> editItem(Map.Entry<String, String> o) {
            return showEditor(o);
        }
    }
}
