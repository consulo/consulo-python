// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.impl;

import com.jetbrains.python.requirements.PyDependenciesFile;
import com.jetbrains.python.requirements.PyDependenciesFileProvider;
import com.jetbrains.python.requirements.SetupPyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

@ExtensionImpl(id = "setupPy", order = "before requirementsTxt")
public class SetupPyFileProvider implements PyDependenciesFileProvider {
    private static final String SETUP_PY_FILE_NAME = "setup.py";

    @Override
    public @Nullable PyDependenciesFile fromFile(VirtualFile file) {
        return file.getName().equals(SETUP_PY_FILE_NAME) ? new SetupPyFile(file) : null;
    }
}
