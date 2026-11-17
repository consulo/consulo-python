// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiUtilCore;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Top-level declarations of {@code VirtualFileExt.kt}.
 */
public final class VirtualFileExtKt {
    private VirtualFileExtKt() {
    }

    public static @Nullable PsiFileSystemItem toPsi(VirtualFile virtualFile, Project project) {
        return PsiUtilCore.findFileSystemItem(project, virtualFile);
    }
}
