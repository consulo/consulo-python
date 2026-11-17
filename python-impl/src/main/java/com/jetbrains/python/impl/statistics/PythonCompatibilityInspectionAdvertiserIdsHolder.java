// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.statistics;

import java.util.List;

// TODO consulo: NotificationIdsHolder (com.intellij.notification.impl) missing, so this holder is not registered
//  (JB: <statistics.notificationIdsHolder implementation="...PythonCompatibilityInspectionAdvertiserIdsHolder"/>)
public final class PythonCompatibilityInspectionAdvertiserIdsHolder {
    public static final String STALE_PYTHON_VERSION = "stale.python.version";
    public static final String USING_FUTURE_IMPORTS = "using.future.imports";
    public static final String USING_SIX_PACKAGE = "using.six.package";

    public List<String> getNotificationIds() {
        return List.of(
            STALE_PYTHON_VERSION,
            USING_FUTURE_IMPORTS,
            USING_SIX_PACKAGE
        );
    }
}
