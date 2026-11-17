// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import jakarta.inject.Singleton;

@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PyDebugShowingNotificationsService {
    private boolean myCythonNotificationShown = false;

    public static PyDebugShowingNotificationsService getInstance() {
        return Application.get().getInstance(PyDebugShowingNotificationsService.class);
    }

    public boolean isCythonNotificationShown() {
        return myCythonNotificationShown;
    }

    public void setCythonNotificationShown(boolean cythonNotificationShown) {
        myCythonNotificationShown = cythonNotificationShown;
    }
}
