// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import com.jetbrains.python.psi.PyPsiFacade;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

/**
 * @author Ilya.Kazakevich
 */
public final class PsiFileSystemItemExtKt {
    private PsiFileSystemItemExtKt() {
    }

    public static @Nullable QualifiedName getQName(PsiFileSystemItem item) {
        String name = PyPsiFacade.getInstance(item.getProject()).findShortestImportableName(item.getVirtualFile(), item);
        if (name == null) {
            return null;
        }
        return QualifiedName.fromDottedString(name);
    }
}
