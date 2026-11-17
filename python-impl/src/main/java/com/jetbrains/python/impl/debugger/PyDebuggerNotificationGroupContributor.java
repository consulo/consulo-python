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
package com.jetbrains.python.impl.debugger;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.ui.notification.NotificationDisplayType;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;
import consulo.project.ui.wm.ToolWindowId;
import consulo.python.impl.localize.PyLocalize;

import java.util.function.Consumer;

/**
 * Consulo registration of JB's {@code Python Debugger} (displayType="TOOL_WINDOW", toolWindowId="Debug") and
 * {@code CythonWarning} notification groups (intellij.python.community.impl.xml).
 */
@ExtensionImpl
public class PyDebuggerNotificationGroupContributor implements NotificationGroupContributor {
    public static final NotificationGroup PYTHON_DEBUGGER = new NotificationGroup(
        "Python Debugger",
        PyLocalize.debugNotificationGroup(),
        NotificationDisplayType.TOOL_WINDOW,
        true,
        ToolWindowId.DEBUG
    );

    /**
     * JB's {@code CythonWarning} notification group (displayType="BALLOON").
     */
    public static final NotificationGroup CYTHON_WARNING = new NotificationGroup(
        "CythonWarning",
        PyLocalize.notificationGroupCython(),
        NotificationDisplayType.BALLOON,
        true
    );

    @Override
    public void contribute(Consumer<NotificationGroup> registrator) {
        registrator.accept(PYTHON_DEBUGGER);
        registrator.accept(CYTHON_WARNING);
    }
}
