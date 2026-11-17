// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl;

import consulo.component.persist.PersistentStateComponent;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;

import java.util.List;
import java.util.Objects;

/**
 * Tools of {@link PersistentStateComponent} that stores folders as {@link VirtualFile}
 */
public class FoldersComponentTools {
    private final List<VirtualFile> myFolders;

    public FoldersComponentTools(List<VirtualFile> folders) {
        myFolders = folders;
    }

    public void setFoldersAsStrings(List<String> folders) {
        myFolders.clear();
        folders.stream()
            .map(it -> StandardFileSystems.local().findFileByPath(it))
            .filter(Objects::nonNull)
            .forEach(myFolders::add);
    }

    public void setFoldersAsVirtualFiles(List<VirtualFile> folders) {
        myFolders.clear();
        myFolders.addAll(folders);
    }
}
