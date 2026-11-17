// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;
import consulo.execution.debug.frame.XStandaloneVariablesView;
import consulo.execution.debug.frame.XValueChildrenList;
import org.jspecify.annotations.Nullable;

@TopicAPI(ComponentScope.APPLICATION)
public interface PyFrameListener {
    void frameChanged();

    default void sessionStopped(@Nullable PyFrameAccessor communication) {
    }

    default void valuesUpdated(PyFrameAccessor communication, XValueChildrenList values) {
    }

    default void viewCreated(PyFrameAccessor communication, XStandaloneVariablesView view) {
    }
}
