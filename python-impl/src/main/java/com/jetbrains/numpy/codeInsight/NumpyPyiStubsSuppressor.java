// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.numpy.codeInsight;

import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiStubSuppressor;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.registry.Registry;
import consulo.virtualFileSystem.VirtualFile;

/**
 * JB file facade: NumpyPyiStubs.kt
 */
@ExtensionImpl
public class NumpyPyiStubsSuppressor implements PyiStubSuppressor {

    @Override
    public boolean isIgnoredStub(PyiFile file) {
        if (Registry.is("enable.numpy.pyi.stubs", true)) {
            return false;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }

        return virtualFile.getPath().contains("/numpy/");
    }
}
