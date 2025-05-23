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

import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.NewVirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class MacPythonSdkFlavor extends CPythonSdkFlavor {
    private MacPythonSdkFlavor() {
    }

    public static MacPythonSdkFlavor INSTANCE = new MacPythonSdkFlavor();
    private static final String[] POSSIBLE_BINARY_NAMES = {"python", "python2", "python3"};

    @Override
    public Collection<String> suggestHomePaths() {
        List<String> candidates = new ArrayList<>();
        collectPythonInstallations("/Library/Frameworks/Python.framework/Versions", candidates);
        collectPythonInstallations("/System/Library/Frameworks/Python.framework/Versions", candidates);
        UnixPythonSdkFlavor.collectUnixPythons("/usr/local/bin", candidates);
        return candidates;
    }

    private static void collectPythonInstallations(String pythonPath, List<String> candidates) {
        VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(pythonPath);
        if (rootVDir != null) {
            if (rootVDir instanceof NewVirtualFile newVirtualFile) {
                newVirtualFile.markDirty();
            }
            rootVDir.refresh(false, false);
            for (VirtualFile dir : rootVDir.getChildren()) {
                String dir_name = dir.getName().toLowerCase();
                if (dir.isDirectory()) {
                    if ("Current".equals(dir_name) || dir_name.startsWith("2") || dir_name.startsWith("3")) {
                        VirtualFile binDir = dir.findChild("bin");
                        if (binDir != null && binDir.isDirectory()) {
                            for (String name : POSSIBLE_BINARY_NAMES) {
                                VirtualFile child = binDir.findChild(name);
                                if (child != null) {
                                    candidates.add(child.getPath());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
