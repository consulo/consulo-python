// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.sdk.skeleton;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Skeleton logic from the original [com.jetbrains.python.sdk.PythonSdkUtil]
 */
public final class PySkeletonUtil {
    private static final Key<PySkeletonHeader> CACHED_SKELETON_HEADER = Key.create("CACHED_SKELETON_HEADER");

    private static @Nullable PySkeletonHeader readSkeletonHeader(VirtualFile file, Sdk pythonSdk) {
        final VirtualFile skeletonsDir = PySdkUtil.findSkeletonsDir(pythonSdk);
        if (skeletonsDir != null && VirtualFileUtil.isAncestor(skeletonsDir, file, false)) {
            PySkeletonHeader skeletonHeader = file.getUserData(CACHED_SKELETON_HEADER);
            if (skeletonHeader == null) {
                skeletonHeader = PySkeletonHeader.readSkeletonHeader(file.toNioPath());
                file.putUserData(CACHED_SKELETON_HEADER, skeletonHeader);
            }
            return skeletonHeader;
        }
        return null;
    }

    public static boolean isStdLib(VirtualFile vFile, @Nullable Sdk pythonSdk) {
        if (pythonSdk == null) {
            return false;
        }

        @Nullable VirtualFile originFile = vFile;
        String originPath = vFile.getPath();
        boolean checkOnRemoteFS = false;
        // All binary skeletons are collected under the same root regardless of their original location.
        // Because of that we need to use paths to the corresponding binary modules recorded in their headers.
        final PySkeletonHeader header = readSkeletonHeader(originFile, pythonSdk);
        if (header != null) {
            // Binary module paths in skeleton headers of Mock SDK don't map to actual physical files.
            // Fallback to the old heuristic for these stubs.
            if (Application.get().isUnitTestMode() &&
                Objects.equals(vFile.getParent(), PySdkUtil.findSkeletonsDir(pythonSdk))) {
                return true;
            }

            final String binaryPath = header.getBinaryFile();
            // XXX Assume that all pre-generated stubs belong to the interpreter's stdlib -- might change in future with PY-32229
            if (binaryPath.equals(PySkeletonHeader.BUILTIN_NAME) || binaryPath.equals(PySkeletonHeader.PREGENERATED)) {
                return true;
            }
            if (PythonSdkType.isRemote(pythonSdk)) {
                checkOnRemoteFS = true;
                // Actual file is on remote file system and not available
                originFile = null;
            }
            else {
                originFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(binaryPath));
            }
            originPath = binaryPath;
        }
        if (originFile != null) {
            originFile = ObjectUtil.notNull(originFile.getCanonicalFile(), originFile);
            originPath = originFile.getPath();
        }

        // TODO consulo: com.intellij.python.sdk.backend.PythonInterpreterExtKt.stdlibLibDirectory missing
        final VirtualFile libDir = PyProjectScopeBuilder.findLibDir(pythonSdk);
        if (libDir != null && isUnderLibDirButNotSitePackages(originFile, originPath, libDir, pythonSdk, checkOnRemoteFS)) {
            return true;
        }
        // TODO consulo: com.intellij.python.sdk.backend.PythonInterpreter (pythonInterpreterResult, venvLibDirectory) missing
        final VirtualFile venvLibDir = PyProjectScopeBuilder.findVirtualEnvLibDir(pythonSdk);
        if (venvLibDir != null && isUnderLibDirButNotSitePackages(originFile, originPath, venvLibDir, pythonSdk, checkOnRemoteFS)) {
            return true;
        }
        if (PyTypeShed.INSTANCE.isInStandardLibrary(vFile)) {
            return true;
        }
        return false;
    }

    private static boolean isUnderLibDirButNotSitePackages(@Nullable VirtualFile file,
                                                           String path,
                                                           VirtualFile libDir,
                                                           Sdk sdk,
                                                           boolean checkOnRemoteFS) {
        final VirtualFile originLibDir;
        final String originLibDirPath;
        if (checkOnRemoteFS) {
            originLibDir = libDir;
            originLibDirPath = mapToRemote(originLibDir.getPath(), sdk);
        }
        else {
            // Normalize the path to the lib directory on local FS
            originLibDir = ObjectUtil.notNull(libDir.getCanonicalFile(), libDir);
            originLibDirPath = originLibDir.getPath();
        }

        // This check is more brittle and thus used as a fallback measure
        if (checkOnRemoteFS || file == null) {
            final String normalizedLidDirPath = FileUtil.toSystemIndependentName(originLibDirPath);
            final String sitePackagesPath = normalizedLidDirPath + "/" + PyNames.SITE_PACKAGES;
            final String normalizedPath = FileUtil.toSystemIndependentName(path);
            return FileUtil.startsWith(normalizedPath, normalizedLidDirPath) && !FileUtil.startsWith(normalizedPath, sitePackagesPath);
        }
        else if (VirtualFileUtil.isAncestor(originLibDir, file, false)) {
            final VirtualFile sitePackagesDir = originLibDir.findChild(PyNames.SITE_PACKAGES);
            return sitePackagesDir == null || !VirtualFileUtil.isAncestor(sitePackagesDir, file, false);
        }
        return false;
    }

    private static String mapToRemote(String localRoot, Sdk sdk) {
        return PythonRuntimeService.getInstance().mapToRemote(localRoot, sdk);
    }

    /**
     * @return name of builtins skeleton file; for Python 2.x it is '{@code __builtins__.py}'.
     */
    public static String getBuiltinsFileName(Sdk sdk) {
        return PyBuiltinCache.getBuiltinsFileName(PythonSdkType.getLanguageLevelForSdk(sdk));
    }
}
