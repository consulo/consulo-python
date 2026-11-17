package com.jetbrains.python.requirements;

import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;

public interface PyDependenciesFile {
    VirtualFile getVirtualFile();

    Image getIcon();
}
