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

package com.jetbrains.python.impl.documentation;

import com.google.common.collect.Sets;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.*;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.AddEditRemovePanel;
import consulo.ui.ex.awt.ColoredTableCellRenderer;
import consulo.ui.ex.awt.DialogWrapper;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonDocumentationConfigurable implements SearchableConfigurable, Configurable.NoScroll, ApplicationConfigurable {
  public static final String ID = "py.external.doc";

  private PythonDocumentationPanel myPanel = new PythonDocumentationPanel();

  @Nonnull
  @Override
  public String getId() {
    return ID;
  }

  @Nullable
  @Override
  public String getParentId() {
    return StandardConfigurableIds.EDITOR_GROUP;
  }

  @Nonnull
  @Nls
  @Override
  public String getDisplayName() {
    return "Python External Documentation";
  }

  @Override
  public String getHelpTopic() {
    return "preferences.ExternalDocumentation";
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void reset() {
    myPanel.getData().clear();
    myPanel.getData().addAll(PythonDocumentationMap.getInstance().getEntries());
  }

  @Override
  public boolean isModified() {
    HashSet<PythonDocumentationMap.Entry> originalEntries = Sets.newHashSet(PythonDocumentationMap.getInstance().getEntries());
    HashSet<PythonDocumentationMap.Entry> editedEntries = Sets.newHashSet(myPanel.getData());
    return !editedEntries.equals(originalEntries);
  }

  @Override
  public void apply() throws ConfigurationException {
    PythonDocumentationMap.getInstance().setEntries(myPanel.getData());
  }

  @Override
  public void disposeUIResources() {
  }

  private static class PythonDocumentationTableModel extends AddEditRemovePanel.TableModel<PythonDocumentationMap.Entry> {
    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return columnIndex == 0 ? "Module Name" : "URL/Path Pattern";
    }

    @Override
    public Object getField(PythonDocumentationMap.Entry o, int columnIndex) {
      return columnIndex == 0 ? o.getPrefix() : o.getUrlPattern();
    }
  }

  private static final PythonDocumentationTableModel ourModel = new PythonDocumentationTableModel();

  private static class PythonDocumentationPanel extends AddEditRemovePanel<PythonDocumentationMap.Entry> {
    public PythonDocumentationPanel() {
      super(ourModel, new ArrayList<PythonDocumentationMap.Entry>());
      setRenderer(1, new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          String text = value == null ? "" : (String) value;
          int pos = 0;
          while(pos < text.length()) {
            int openBrace = text.indexOf('{', pos);
            if (openBrace == -1) openBrace = text.length();
            append(text.substring(pos, openBrace));
            int closeBrace = text.indexOf('}', openBrace);
            if (closeBrace == -1)
              closeBrace = text.length();
            else
              closeBrace++;
            append(text.substring(openBrace, closeBrace), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.blue.darker()));
            pos = closeBrace;
          }
        }
      });
    }

    @Override
    protected PythonDocumentationMap.Entry addItem() {
      return showEditor(null);
    }

    @Nullable
    private PythonDocumentationMap.Entry showEditor(PythonDocumentationMap.Entry entry) {
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
    protected boolean removeItem(PythonDocumentationMap.Entry o) {
      return true;
    }

    @Override
    protected PythonDocumentationMap.Entry editItem(PythonDocumentationMap.Entry o) {
      return showEditor(o);
    }
  }

}
