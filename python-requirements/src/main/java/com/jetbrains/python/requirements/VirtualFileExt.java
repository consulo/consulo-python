// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements;

import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

public final class VirtualFileExt {
    private VirtualFileExt() {
    }

    public static boolean isRequirementsTxtFile(VirtualFile file) {
        CharSequence sequence = file.getNameSequence();
        String ext = FileUtil.getExtension(sequence).toString();
        if (!ext.equals("txt") && !ext.equals("in")) {
            return false;
        }
        String path = file.getPath();
        return path.contains("/requirements/") || StringUtil.contains(sequence, "requirements");
    }
}
