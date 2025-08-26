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

import consulo.annotation.component.ExtensionImpl;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.io.File;

/**
 * @author traff
 */
@ExtensionImpl
public class MayaSdkFlavor extends PythonSdkFlavor {
    @Override
    public boolean isValidSdkHome(String path) {
        File file = new File(path);
        return (file.isFile() && isValidSdkPath(file)) || isMayaFolder(file);
    }

    private static boolean isMayaFolder(File file) {
        return file.isDirectory() && file.getName().equals("Maya.app");
    }

    @Override
    public boolean isValidSdkPath(@Nonnull File file) {
        String name = FileUtil.getNameWithoutExtension(file).toLowerCase();
        return name.startsWith("mayapy");
    }

    @Override
    public String getVersionOption() {
        return "--version";
    }

    @Nonnull
    @Override
    public String getName() {
        return "MayaPy";
    }

    @Nonnull
    @Override
    public Image getIcon() {
        return PythonPsiIconGroup.python(); //TODO: maya icon
    }

    @Override
    public VirtualFile getSdkPath(VirtualFile path) {
        if (isMayaFolder(new File(path.getPath()))) {
            return path.findFileByRelativePath("Contents/bin/mayapy");
        }
        return path;
    }
}
