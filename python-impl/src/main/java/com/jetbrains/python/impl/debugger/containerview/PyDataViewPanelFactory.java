// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger.containerview;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PyDataViewPanelFactory {
    public abstract PyDataViewerAbstractPanel createDataViewerPanel(PyDataViewerModel state);
}
