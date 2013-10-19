package com.jetbrains.python.refactoring.classes.ui;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;

import org.jetbrains.annotations.Nullable;
import com.intellij.ide.IconDescriptorUpdaters;
import com.intellij.openapi.util.Iconable;
import com.jetbrains.python.psi.PyClass;

/**
 * @author Dennis.Ushakov
 */
public class PyClassCellRenderer extends DefaultListCellRenderer {
  private final boolean myShowReadOnly;
  public PyClassCellRenderer() {
    setOpaque(true);
    myShowReadOnly = true;
  }

  public PyClassCellRenderer(boolean showReadOnly) {
    setOpaque(true);
    myShowReadOnly = showReadOnly;
  }

  public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

    return customizeRenderer(this, value, myShowReadOnly);
  }

  public static JLabel customizeRenderer(final JLabel cellRendererComponent, final Object value, final boolean showReadOnly) {
    PyClass aClass = (PyClass) value;
    cellRendererComponent.setText(getClassText(aClass));

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (showReadOnly) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }
    Icon icon = IconDescriptorUpdaters.getIcon(aClass, flags);
    if(icon != null) {
      cellRendererComponent.setIcon(icon);
    }
    return cellRendererComponent;
  }

  @Nullable
  public static String getClassText(PyClass aClass) {
    return aClass.getName();
  }
}
