// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev.tables;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyNumericContainerPopupCustomizer {
    ExtensionPointName<PyNumericContainerPopupCustomizer> EP_NAME = ExtensionPointName.create(PyNumericContainerPopupCustomizer.class);

    /**
     * Returns true if the value could be processed.
     */
    default boolean showFullValuePopup(PyFrameAccessor frameAccessor, PyDebugValue debugValue) {
        frameAccessor.showNumericContainer(debugValue);
        return true;
    }
}
