// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.ui.console.ConsoleRootType;
import consulo.language.scratch.RootType;
import consulo.python.impl.localize.PyLocalize;
import jakarta.inject.Inject;

@ExtensionImpl
public class PyConsoleRootType extends ConsoleRootType {
    @Inject
    PyConsoleRootType() {
        super("py", PyLocalize.pythonConsoleHistoryRoot().get());
    }

    public static PyConsoleRootType getInstance() {
        return RootType.findByClass(PyConsoleRootType.class);
    }
}
