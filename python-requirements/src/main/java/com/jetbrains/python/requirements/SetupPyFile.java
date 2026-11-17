package com.jetbrains.python.requirements;

import consulo.python.requirements.icon.PythonRequirementsIconGroup;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class SetupPyFile implements PyDependenciesFile {
    private final VirtualFile myVirtualFile;
    private final Image myIcon = PythonRequirementsIconGroup.requirements();

    public SetupPyFile(VirtualFile virtualFile) {
        myVirtualFile = virtualFile;
    }

    @Override
    public VirtualFile getVirtualFile() {
        return myVirtualFile;
    }

    @Override
    public Image getIcon() {
        return myIcon;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SetupPyFile that)) {
            return false;
        }
        return Objects.equals(myVirtualFile, that.myVirtualFile);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(myVirtualFile);
    }

    @Override
    public String toString() {
        return "SetupPyFile(virtualFile=" + myVirtualFile + ")";
    }
}
