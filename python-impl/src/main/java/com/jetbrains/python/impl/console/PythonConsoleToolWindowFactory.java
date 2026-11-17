// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.console.actions.PyConsoleRenameAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;

@ExtensionImpl
public class PythonConsoleToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String ID = "Python Console";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    @RequiredUIAccess
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        // Consulo-specific: Consulo has no "ToolWindowContextMenu" group to add PyConsoleRenameAction to
        toolWindow.setTabDoubleClickActions(new PyConsoleRenameAction());

        Object isStartedFromRunner = toolWindow.getComponent().getClientProperty(PydevConsoleRunnerImpl.STARTED_BY_RUNNER);
        // we need it to distinguish Console toolwindows started by Console Runner from ones started by toolwindow activation
        if (!"true".equals(isStartedFromRunner)) {
            // createToolWindowContent cannot wait, while building the runner waits for the project
            // model. The runner therefore comes back on the EDT, which is also the write-safe context the former
            // TransactionGuard.submitTransaction stood for.
            PythonConsoleStarter.launchPythonConsoleRunnerIfInterpreterExists(project, null, it -> it.runSync(true));
        }
    }

    @Override
    public ToolWindowAnchor getAnchor() {
        return ToolWindowAnchor.RIGHT;
    }

    @Override
    public Image getIcon() {
        return PythonImplIconGroup.pythonPythonconsoletoolwindow();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.pythonConsole();
    }

    @Override
    public boolean canCloseContents() {
        return true;
    }
}
