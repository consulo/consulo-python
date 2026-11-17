// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.run;

import com.jetbrains.python.impl.sdk.PythonEnvUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PlainEnvironmentController implements EnvironmentController {
    private final Map<String, String> myEnvs;

    public PlainEnvironmentController(Map<String, String> envs) {
        myEnvs = envs;
    }

    @Override
    public void putFixedValue(String name, String value) {
        myEnvs.put(name, value);
    }

    @Override
    public void putTargetPathValue(String name, String localPath) {
        myEnvs.put(name, localPath);
    }

    @Override
    public void putTargetPathsValue(String name, Collection<String> localPaths) {
        putTargetPathsValue(name, localPaths, File.pathSeparator);
    }

    @Override
    public void putTargetPathsValue(String name, Collection<String> localPaths, CharSequence separator) {
        myEnvs.put(name, String.join(separator, localPaths));
    }

    @Override
    public void putResolvedTargetPathsValue(String name, Collection<String> targetPaths) {
        myEnvs.put(name, String.join(File.pathSeparator, targetPaths));
    }

    @Override
    public void appendTargetPathToPathsValue(String name, String localPath) {
        myEnvs.merge(name, localPath, (originalValue, additionalPath) -> String.join(File.pathSeparator, originalValue, additionalPath));
    }

    @Override
    public void addHelperEntriesToPythonPath(List<String> pythonPathEntries) {
        String helperPackagePath = String.join(File.pathSeparator, pythonPathEntries);
        myEnvs.merge(PythonEnvUtil.PYTHONPATH, helperPackagePath,
            (originalValue, additionalPath) -> String.join(File.pathSeparator, originalValue, additionalPath));
    }

    @Override
    public boolean isEnvSet(String name) {
        return myEnvs.containsKey(name);
    }
}
