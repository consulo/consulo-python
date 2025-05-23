/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.sdk.flavors;

import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.NewVirtualFile;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author yole
 */
public class WinPythonSdkFlavor extends CPythonSdkFlavor {
    public static WinPythonSdkFlavor INSTANCE = new WinPythonSdkFlavor();

    private WinPythonSdkFlavor() {
    }

    @Override
    public Collection<String> suggestHomePaths() {
        Set<String> candidates = new TreeSet<>();
        findInCandidatePaths(candidates, "python.exe", "jython.bat", "pypy.exe");
        return candidates;
    }

    private static void findInCandidatePaths(Set<String> candidates, String... exe_names) {
        for (String name : exe_names) {
            findInstallations(candidates, name, "C:\\", "C:\\Program Files\\");
            findInPath(candidates, name);
        }
    }

    private static void findInstallations(Set<String> candidates, String exe_name, String... roots) {
        for (String root : roots) {
            findSubdirInstallations(candidates, root, FileUtil.getNameWithoutExtension(exe_name), exe_name);
        }
    }

    public static void findInPath(Collection<String> candidates, String exeName) {
        String path = System.getenv("PATH");
        for (String pathEntry : StringUtil.split(path, ";")) {
            if (pathEntry.startsWith("\"") && pathEntry.endsWith("\"")) {
                if (pathEntry.length() < 2) {
                    continue;
                }
                pathEntry = pathEntry.substring(1, pathEntry.length() - 1);
            }
            File f = new File(pathEntry, exeName);
            if (f.exists()) {
                candidates.add(FileUtil.toSystemIndependentName(f.getPath()));
            }
        }
    }

    private static void findSubdirInstallations(Collection<String> candidates, String rootDir, String dirPrefix, String exeName) {
        VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(rootDir);
        if (rootVDir != null) {
            if (rootVDir instanceof NewVirtualFile newVirtualFile) {
                newVirtualFile.markDirty();
            }
            rootVDir.refresh(false, false);
            for (VirtualFile dir : rootVDir.getChildren()) {
                if (dir.isDirectory() && dir.getName().toLowerCase().startsWith(dirPrefix)) {
                    VirtualFile pythonExe = dir.findChild(exeName);
                    if (pythonExe != null) {
                        candidates.add(FileUtil.toSystemIndependentName(pythonExe.getPath()));
                    }
                }
            }
        }
    }
}
