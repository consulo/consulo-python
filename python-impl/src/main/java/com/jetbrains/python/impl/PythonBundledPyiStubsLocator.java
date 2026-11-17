// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.python.impl;

import consulo.container.plugin.PluginManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the bundled `.pyi` stubs.
 * <p>
 * The stubs live in the `intellij.python.bundledPyiStubs` module. The module holds `typeshed` and `third_party` stubs.
 * <p>
 * Consulo: the stubs are expected in the {@code bundledPyiStubs} directory of the plugin distribution.
 */
public final class PythonBundledPyiStubsLocator {
    private static final String RELATIVE_PATH = "bundledPyiStubs";

    private PythonBundledPyiStubsLocator() {
    }

    /**
     * The directory of all bundled stubs: `typeshed` and `third_party` are in it.
     */
    public static Path root() {
        File pluginPath = PluginManager.getPluginPath(PythonBundledPyiStubsLocator.class);
        return pluginPath.toPath().resolve(RELATIVE_PATH);
    }

    public static Path findPath(String resourceName) {
        Path root = root();
        Path resourcePath = root.resolve(resourceName);
        if (Files.exists(resourcePath)) {
            return resourcePath;
        }
        throw new IllegalStateException("'" + resourceName + "' does not exist under the bundled .pyi stub root '" + root + "'");
    }
}
