// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.python.impl.debugger.variablesview.usertyperenderers;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.impl.debugger.PyDebugProcess;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.ui.XValueTree;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;

/**
 * Consulo-specific: JB extends XDebuggerTreeBackendOnlyActionBase, which is not public API in Consulo. The selected
 * nodes come from {@link XValueTree#KEY}, and actionPerformed follows XDebuggerTreeActionBase (skip nodes with an empty name).
 */
@ActionImpl(
    id = "PyDebugger.CustomizeDataView",
    parents = @ActionParentRef(value = @ActionRef(id = "XDebugger.ValueGroup"), anchor = ActionRefAnchor.FIRST)
)
public class ConfigureTypeRenderersAction extends AnAction implements AnActionWithSyncUpdate {
    public ConfigureTypeRenderersAction() {
        super(PyLocalize.actionPydebuggerCustomizedataviewText());
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
        XDebugSession session = e.getData(XDebugSession.DATA_KEY);
        XValueTree tree = e.getData(XValueTree.KEY);
        e.getPresentation().setEnabledAndVisible(session != null && session.getDebugProcess() instanceof PyDebugProcess &&
            tree != null && tree.getSelectedNodes().size() == 1);
    }

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
        Project eventProject = e.getData(Project.KEY);
        Project project = eventProject != null ? eventProject : ProjectManager.getInstance().getDefaultProject();
        if (!(value instanceof PyDebugValue debugValue)) {
            return;
        }
        String typeRendererId = debugValue.getTypeRendererId();
        if (typeRendererId == null) {
            ConfigureTypeRenderersActionKt.showSettingsWithNewRenderer(project, debugValue);
            return;
        }
        Integer rendererIndexToSelect = ConfigureTypeRenderersActionKt.getRendererIndexWithId(typeRendererId);
        if (rendererIndexToSelect != null) {
            ConfigureTypeRenderersActionKt.showSettingsWithSelectedRenderer(project, rendererIndexToSelect);
        }
        else {
            ConfigureTypeRenderersActionKt.showSettingsWithNewRenderer(project, debugValue);
        }
    }
}
