// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements;

import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.PsiFileBase;
import consulo.virtualFileSystem.fileType.FileType;

public class RequirementsFile extends PsiFileBase {
    public RequirementsFile(FileViewProvider viewProvider) {
        super(viewProvider, RequirementsLanguage.INSTANCE);
    }

    @Override
    public FileType getFileType() {
        return RequirementsFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Requirements File";
    }
}
