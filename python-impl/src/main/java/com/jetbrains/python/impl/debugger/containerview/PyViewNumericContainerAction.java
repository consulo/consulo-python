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
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.impl.debugger.array.NumpyArrayTable;
import com.jetbrains.python.impl.debugger.dataframe.DataFrameTable;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.XDebuggerTree;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.tree.TreePath;

/**
 * @author amarch
 */

public class PyViewNumericContainerAction extends XDebuggerTreeActionBase {

  @Override
  protected void perform(consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl node,
                         @Nonnull String nodeName,
                         AnActionEvent e) {
    Project p = e.getData(Project.KEY);
    if (p != null && node != null && node.getValueContainer() instanceof PyDebugValue && node.isComputed()) {
      PyDebugValue debugValue = (PyDebugValue)node.getValueContainer();
      showNumericViewer(p, debugValue);
    }
  }

  public static void showNumericViewer(Project project, PyDebugValue debugValue) {
    String nodeType = debugValue.getType();
    final ViewNumericContainerDialog dialog;
    if ("ndarray".equals(nodeType)) {
      dialog = new ViewNumericContainerDialog(project, (dialogWrapper) -> {
        NumpyArrayTable arrayTable = new NumpyArrayTable(project, dialogWrapper, debugValue);
        arrayTable.init();
        return arrayTable.getComponent().getMainPanel();
      });
    }
    else if (("DataFrame".equals(nodeType))) {
      dialog = new ViewNumericContainerDialog(project, (dialogWrapper) -> {
        DataFrameTable dataFrameTable = new DataFrameTable(project, dialogWrapper, debugValue);
        dataFrameTable.init();
        return dataFrameTable.getComponent().getMainPanel();
      });
    }
    else {
      throw new IllegalStateException("Cannot render node type: " + nodeType);
    }

    dialog.show();
  }

  @Nullable
  private static TreePath[] getSelectedPaths(DataContext dataContext) {
    XDebuggerTree tree = consulo.ide.impl.idea.xdebugger.impl.ui.tree.XDebuggerTree.getTree(dataContext);
    return tree == null ? null : tree.getSelectionPaths();
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(false);
    TreePath[] paths = getSelectedPaths(e.getDataContext());
    if (paths != null) {
      if (paths.length > 1) {
        e.getPresentation().setVisible(false);
        return;
      }

      consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl node = getSelectedNode(e.getDataContext());
      if (node != null && node.getValueContainer() instanceof PyDebugValue && node.isComputed()) {
        PyDebugValue debugValue = (PyDebugValue)node.getValueContainer();

        String nodeType = debugValue.getType();
        if ("ndarray".equals(nodeType)) {
          e.getPresentation().setText("View as Array");
          e.getPresentation().setVisible(true);
        }
        else if (("DataFrame".equals(nodeType))) {
          e.getPresentation().setText("View as DataFrame");
          e.getPresentation().setVisible(true);
        }
        else {
          e.getPresentation().setVisible(false);
        }
      }
      else {
        e.getPresentation().setVisible(false);
      }
    }
  }
}
