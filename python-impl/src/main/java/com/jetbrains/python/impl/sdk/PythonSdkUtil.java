// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.sdk;

import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Old API only for external usages, non-deprecated yet because there is no alternative API.
 * <p>
 * Consulo: JB delegates to {@code com.jetbrains.python.sdk.legacy.PythonSdkUtil}; in Consulo that legacy code lives in
 * {@link PythonSdkType} and {@link PySdkUtil}, so this facade delegates there.
 */
public final class PythonSdkUtil {
    private PythonSdkUtil() {
    }

    public static boolean isPythonSdk(Sdk sdk) {
        return sdk.getSdkType() instanceof PythonSdkType;
    }

    /**
     * @deprecated Use {@code getAllSdks(Project)} or {@code getAllSdks(PyProject)}
     */
    @Deprecated(forRemoval = true)
    public static List<Sdk> getAllSdks() {
        return PythonSdkType.getAllSdks();
    }

    // TODO consulo: getAllSdks(Project) and getAllSdks(PyProject) - PyProject and the project-scoped legacy
    //  PythonSdkUtil.getAllSdks(project) missing

    /**
     * The SDK of the interpreter of the Python project on {@code module}, read from the project structure. {@code null}
     * before the first structure lands.
     *
     * @deprecated Use the interpreter of the Python project, {@code PyProject.getInterpreter}.
     */
    @Deprecated
    public static @Nullable Sdk findPythonSdk(@Nullable Module module) {
        return module == null ? null : MainPythonSdkKt.pythonSdkIfReady(module);
    }

    public static boolean isRemote(@Nullable Sdk sdk) {
        return PythonSdkType.isRemote(sdk);
    }


    @SuppressWarnings("unused") // Used by ext. plugin
    public static String getUserSite() {
        return PySdkUtil.getUserSite();
    }

    public static @Nullable VirtualFile findSkeletonsDir(final Sdk sdk) {
        return PySdkUtil.findSkeletonsDir(sdk);
    }

    // TODO consulo: isDisposed(Sdk) - legacy PythonSdkUtil.isDisposed (ProjectJdkImpl disposal check) missing

    // It is only here for external plugins
    @SuppressWarnings("unused") // Used by ext. plugin
    public static @Nullable String getPythonExecutable(String rootPath) {
        return PythonSdkType.getPythonExecutable(rootPath);
    }



    /**
     * Returns the "site-packages" directory that is going to be used for installing new packages with {@code pip}.
     * <p>
     * Note that on a virtual env there might be two such directories in {@code sys.path} depending on whether
     * the option "--system-site-packages" was given during its creation. Then the one inside the actual virtual
     * env tree will be returned, as it's the one used to install new packages.
     * Also, on some systems, first of all in system distributions of Python on Linux, there might be no
     * "site-packages" at all, and this method returns {@code null} accordingly in this case.
     */
    // TODO consulo: getSitePackagesDirectory(Sdk) - PySkeletonUtil.getSitePackagesDirectory / PythonInterpreterExtKt.sitePackagesDirectory missing

    /**
     * @deprecated use {@code PythonInterpreter}
     */
    @Deprecated
    public static boolean isVirtualEnv(Sdk sdk) {
        // TODO consulo: PythonInterpreter / PythonEnvironment kind id ("venv") missing, the legacy check is used
        return PythonSdkType.isVirtualEnv(sdk);
    }

    /**
     * @deprecated use {@code PythonInterpreter}
     */
    @Deprecated
    public static boolean isCondaVirtualEnv(Sdk sdk) {
        // TODO consulo: PythonInterpreter / PythonEnvironment kind id ("conda") missing, the legacy check is used
        return PythonSdkType.isCondaVirtualEnv(sdk);
    }
}
