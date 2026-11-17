// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger.array;

import com.jetbrains.python.debugger.PyDebugValue;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.table.JBTable;
import org.jspecify.annotations.Nullable;

public abstract class AbstractDataViewTable extends JBTable {
    public abstract JBScrollPane getScrollPane();

    public abstract void setAutoResize(boolean resize);

    public abstract void setEmpty();

    public void setModel(AsyncArrayTableModel model, boolean modifier) {
        setModel(model);
    }

    public String getVariableName() {
        return "unnamed";
    }

    public @Nullable PyDebugValue getDebugValue() {
        return null;
    }
}
