// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Extension point for recognising Python dependency files. Each provider inspects the given
 * {@link VirtualFile} (typically by name and optionally by content) and returns its own
 * {@link PyDependenciesFile} implementation, or {@code null} if it doesn't recognise the file.
 * <p>
 * Use {@link #resolve} to walk all registered providers and obtain the first non-null match.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyDependenciesFileProvider {
    /**
     * Returns a {@link PyDependenciesFile} if this provider recognises {@code file}; otherwise {@code null}.
     * Implementations should be fast for the common rejection case (e.g. compare the file
     * name first) and may suspend for any heavier validation across file content.
     */
    // TODO consulo: JB declares this as a Kotlin suspend function
    @Nullable PyDependenciesFile fromFile(VirtualFile file);

    /**
     * Walks the registered {@link PyDependenciesFileProvider}s in extension-point order and
     * returns the first non-null {@link PyDependenciesFile} produced by any of them, or {@code null}
     * if no provider recognises {@code file}.
     */
    static @Nullable PyDependenciesFile resolve(VirtualFile file) {
        return Application.get().getExtensionPoint(PyDependenciesFileProvider.class).computeSafeIfAny(it -> it.fromFile(file));
    }
}
