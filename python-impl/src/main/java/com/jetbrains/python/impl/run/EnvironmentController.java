// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.run;

import java.util.Collection;
import java.util.List;

/**
 * This is a temporary interface for smoother transition to Targets API. Its
 * lifetime is expected to be limited by the lifetime of the legacy
 * implementation based on {@code GeneralCommandLine}.
 */
public interface EnvironmentController {
    /**
     * Puts the constant {@code value} to the environment variable with the provided
     * {@code name}.
     */
    void putFixedValue(String name, String value);

    /**
     * Puts the path on the target for the provided {@code localPath} to the
     * environment variable with the provided {@code name}.
     */
    void putTargetPathValue(String name, String localPath);

    /**
     * Composes the value based on the paths on the target for the provided
     * {@code localPaths} by joining them using the path separator on the target OS and
     * puts the value to the environment variable with provided {@code name}.
     */
    void putTargetPathsValue(String name, Collection<String> localPaths);

    void putTargetPathsValue(String name, Collection<String> localPaths, CharSequence separator);

    /**
     * Composes the value based on {@code targetPaths} by joining them using the path
     * separator on the target OS and puts the value to the environment variable
     * with the provided {@code name}.
     */
    void putResolvedTargetPathsValue(String name, Collection<String> targetPaths);

    /**
     * Appends the path on the target for the provided {@code localPath} to the value
     * of the environment variable with the provided {@code name}.
     */
    void appendTargetPathToPathsValue(String name, String localPath);

    /**
     * Adds the provided path entries to {@code PYTHONPATH} environment variable. The path entries <b>must</b> be located within PyCharm helpers.
     *
     * @throws IllegalStateException if some of the provided path are located outside PyCharm helpers
     */
    void addHelperEntriesToPythonPath(List<String> pythonPathEntries);

    /**
     * Returns whether the value of the environment variable with the provided
     * {@code name} is set or not.
     */
    boolean isEnvSet(String name);
}
