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
package com.jetbrains.python.impl.actions.internal;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;

import java.util.function.Consumer;

/**
 * Consulo registration of JB's {@code Python.Internal} notification group (intellij.python.psi.impl.xml).
 */
@ExtensionImpl
public class PyPsiInternalNotificationGroupContributor implements NotificationGroupContributor {
    public static final NotificationGroup PYTHON_INTERNAL =
        NotificationGroup.balloonGroup("Python.Internal", LocalizeValue.localizeTODO("Python Internal"));

    @Override
    public void contribute(Consumer<NotificationGroup> registrator) {
        registrator.accept(PYTHON_INTERNAL);
    }
}
