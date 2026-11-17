/*
 * Copyright 2000-2026 JetBrains s.r.o.
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
/*
 * Copyright 2013-2026 consulo.io
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

import com.jetbrains.python.debugger.NodeTypes;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyXDebugValue;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.ui.XValueTree;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import org.jspecify.annotations.Nullable;

/**
 * @author amarch
 */
@ActionImpl(
    id = "PyDebugger.ViewArray",
    parents = @ActionParentRef(
        value = @ActionRef(id = "XDebugger.ValueGroup"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "Debugger.Tree.AddToWatches")
    )
)
public class PyViewNumericContainerAction extends AnAction implements AnActionWithSyncUpdate {
    public PyViewNumericContainerAction() {
        super(PyLocalize.actionPydebuggerViewarrayText());
    }

    // JB extends XDebuggerTreeBackendOnlyActionBase, which is not public API in Consulo: the selected node comes from
    // XValueTree.KEY, and actionPerformed follows XDebuggerTreeActionBase (skip nodes with an empty name)
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        XValueTree tree = e.getData(XValueTree.KEY);
        XValueNode node = tree != null ? tree.getSelectedNode() : null;
        if (node == null) {
            return;
        }
        LocalizeValue nodeName = node.getName();
        XValue value = node.getValueContainer();
        if (nodeName.isNotEmpty() && value != null) {
            perform(value, nodeName.get(), e);
        }
    }

    protected void perform(XValue value, String nodeName, AnActionEvent e) {
        Project p = e.getData(Project.KEY);
        if (p != null && value instanceof PyXDebugValue debugValue) {
            showNumericViewer(p, debugValue);
        }
    }

    public static void showNumericViewer(Project project, PyDebugValue debugValue) {
        PyDataView.getInstance(project).show(debugValue);
    }

    private static void showNumericViewer(Project project, PyXDebugValue debugValue) {
        showNumericViewer(project, debugValue.toPyDebugValue());
    }

    private static @Nullable XValue getSelectedValue(AnActionEvent e) {
        XValueTree tree = e.getData(XValueTree.KEY);
        return tree != null ? tree.getSelectedValue() : null;
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
        XValue value = getSelectedValue(e);
        if (!(value instanceof PyXDebugValue debugValue)) {
            e.getPresentation().setVisible(false);
            return;
        }

        if (!isViewNumericContainerSupported(debugValue)) {
            e.getPresentation().setVisible(false);
            return;
        }

        String nodeType = debugValue.getType();
        if (isArrayNodeType(nodeType)) {
            e.getPresentation().setText(PyLocalize.debuggerNumericViewAsArray());
            e.getPresentation().setVisible(true);
        }
        else if (isDataFrameNodeType(nodeType)) {
            e.getPresentation().setText(PyLocalize.debuggerNumericViewAsDataframe());
            e.getPresentation().setVisible(true);
        }
        else if (isSeriesNodeType(nodeType)) {
            e.getPresentation().setText(PyLocalize.debuggerNumericViewAsSeries());
            e.getPresentation().setVisible(true);
        }
        else {
            e.getPresentation().setVisible(false);
        }
    }

    public static boolean isViewNumericContainerSupported(PyXDebugValue debugValue) {
        String nodeType = debugValue.getType();
        return isArrayNodeType(nodeType) || isDataFrameNodeType(nodeType) || isSeriesNodeType(nodeType);
    }

    private static boolean isArrayNodeType(String nodeType) {
        return NodeTypes.NDARRAY_NODE_TYPE.equals(nodeType) ||
            NodeTypes.RECARRAY_NODE_TYPE.equals(nodeType) ||
            NodeTypes.EAGER_TENSOR_NODE_TYPE.equals(nodeType) ||
            NodeTypes.RESOURCE_VARIABLE_NODE_TYPE.equals(nodeType) ||
            NodeTypes.SPARSE_TENSOR_NODE_TYPE.equals(nodeType) ||
            NodeTypes.TENSOR_NODE_TYPE.equals(nodeType);
    }

    private static boolean isDataFrameNodeType(String nodeType) {
        return NodeTypes.DATA_FRAME_NODE_TYPE.equals(nodeType) ||
            NodeTypes.GEO_DATA_FRAME_NODE_TYPE.equals(nodeType) ||
            NodeTypes.DATASET_NODE_TYPE.equals(nodeType);
    }

    private static boolean isSeriesNodeType(String nodeType) {
        return NodeTypes.SERIES_NODE_TYPE.equals(nodeType) || NodeTypes.GEO_SERIES_NODE_TYPE.equals(nodeType);
    }
}
