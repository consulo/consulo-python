// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.namespacePackages;

import com.jetbrains.python.psi.PyCustomPackageIdentifier;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;

@ExtensionImpl
public class PyNamespacePackageCustomIdentifier implements PyCustomPackageIdentifier {
    // TODO consulo: ExtensionNotApplicableException missing - JB throws it from the constructor
    //  when "python.explicit.namespace.packages" is disabled; isPackage() checks the same key below

    @Override
    public boolean isPackage(PsiDirectory directory) {
        if (!Registry.is("python.explicit.namespace.packages", true)) {
            return false;
        }
        Module module = ModuleUtilCore.findModuleForPsiElement(directory);
        if (module == null) {
            return false;
        }
        return PyNamespacePackagesService.getInstance(module).isNamespacePackage(directory.getVirtualFile());
    }

    @Override
    public boolean isPackageFile(PsiFile file) {
        return false;
    }
}
