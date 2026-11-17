// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.impl;

import com.jetbrains.python.requirements.PyDependenciesFile;
import com.jetbrains.python.requirements.PyDependenciesFileProvider;
import com.jetbrains.python.requirements.RequirementsTxtFile;
import com.jetbrains.python.requirements.VirtualFileExt;
import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

@ExtensionImpl(id = "requirementsTxt")
public class RequirementsTxtFileProvider implements PyDependenciesFileProvider {
    @Override
    public @Nullable PyDependenciesFile fromFile(VirtualFile file) {
        return VirtualFileExt.isRequirementsTxtFile(file) ? new RequirementsTxtFile(file) : null;
    }
}
