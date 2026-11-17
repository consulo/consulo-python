// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package com.jetbrains.python.impl.console.actions;

import com.jetbrains.python.impl.console.PyExecuteConsoleCustomizer;
import com.jetbrains.python.impl.console.PythonConsoleToolWindowFactory;
import consulo.application.dumb.DumbAware;
import consulo.execution.ui.RunContentDescriptor;
import consulo.project.ui.wm.action.ToolWindowTabRenameAction;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.content.Content;

import static com.jetbrains.python.impl.console.PythonConsoleToolWindow.CONTENT_DESCRIPTOR;

/**
 * Consulo-specific: JB adds this action to the "ToolWindowContextMenu" group, which Consulo has not.
 * {@link PythonConsoleToolWindowFactory} sets it as the tab double-click action of the Python Console tool window.
 * Consulo's {@link ToolWindowTabRenameAction} takes the rename as a consumer, which stands for JB's
 * {@code applyContentDisplayName} override.
 */
public final class PyConsoleRenameAction extends ToolWindowTabRenameAction implements DumbAware {
    public PyConsoleRenameAction() {
        super(PythonConsoleToolWindowFactory.ID, PyLocalize.pythonConsoleRenameMessage(), PyConsoleRenameAction::applyContentDisplayName);
    }

    private static void applyContentDisplayName(Content content, String newContentName) {
        content.setDisplayName(newContentName);

        RunContentDescriptor contentDescriptor = content.getUserData(CONTENT_DESCRIPTOR);
        if (contentDescriptor == null) {
            return;
        }
        PyExecuteConsoleCustomizer.getInstance().descriptorNameUpdated(contentDescriptor, newContentName);
    }
}
