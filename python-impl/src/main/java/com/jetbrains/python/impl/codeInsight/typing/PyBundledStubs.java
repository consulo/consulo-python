// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.impl.PythonBundledPyiStubsLocator;
import consulo.util.lang.lazy.LazyValue;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Supplier;

public final class PyBundledStubs {
    public static final PyBundledStubs INSTANCE = new PyBundledStubs();

    private static final String THIRD_PARTY_STUBS_DIR = "third_party";

    /**
     * The actual bundled stubs directory.
     */
    private final Supplier<@Nullable VirtualFile> root = LazyValue.nullable(() -> {
        Path stubsPath = PythonBundledPyiStubsLocator.findPath(THIRD_PARTY_STUBS_DIR);
        return StandardFileSystems.local().findFileByPath(stubsPath.toAbsolutePath().toString());
    });

    private PyBundledStubs() {
    }

    public boolean isInside(VirtualFile file) {
        VirtualFile root = getRoot();
        return root != null && VirtualFileUtil.isAncestor(root, file, false);
    }

    /**
     * The actual bundled stubs directory.
     */
    public @Nullable VirtualFile getRoot() {
        return root.get();
    }

    /**
     * Find the directory containing .pyi stubs for the package {@code packageName} among the bundled stubs.
     * <p>
     * {@code packageName} should match the name of the package on PyPI.
     */
    public @Nullable VirtualFile getStubRootForPackage(String packageName) {
        VirtualFile root = getRoot();
        return root != null ? root.findChild(packageName) : null;
    }
}
