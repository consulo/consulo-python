// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCustomPackageIdentifier;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;

@ExtensionImpl
public class PyiCustomPackageIdentifier implements PyCustomPackageIdentifier {
    @Override
    public boolean isPackage(PsiDirectory directory) {
        return directory.findFile(PyNames.INIT_DOT_PYI) != null;
    }

    @Override
    public boolean isPackageFile(PsiFile file) {
        return file.getName().equals(PyNames.INIT_DOT_PYI);
    }
}
