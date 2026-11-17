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
package com.jetbrains.python.impl.inspections;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.ui.notification.NotificationDisplayType;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;
import consulo.python.impl.localize.PyCodeInsightLocalize;

import java.util.function.Consumer;

/**
 * Consulo registration of JB's {@code Python Compatibility Inspection Advertiser} notification group
 * (intellij.python.codeInsight.xml, displayType="STICKY_BALLOON", isLogByDefault="false").
 */
@ExtensionImpl
public class PyCompatibilityInspectionAdvertiserNotificationGroupContributor implements NotificationGroupContributor {
    public static final NotificationGroup PYTHON_COMPATIBILITY_INSPECTION_ADVERTISER = new NotificationGroup(
        "Python Compatibility Inspection Advertiser",
        PyCodeInsightLocalize.pythonCompatibilityInspectionAdvertiserNotificationsGroupTitle(),
        NotificationDisplayType.STICKY_BALLOON,
        false
    );

    @Override
    public void contribute(Consumer<NotificationGroup> registrator) {
        registrator.accept(PYTHON_COMPATIBILITY_INSPECTION_ADVERTISER);
    }
}
