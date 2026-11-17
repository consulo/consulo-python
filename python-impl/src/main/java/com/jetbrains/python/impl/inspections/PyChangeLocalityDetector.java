// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.ChangeLocalityDetector;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PyChangeLocalityDetector implements ChangeLocalityDetector {
    @Override
    public @Nullable PsiElement getChangeHighlightingDirtyScopeFor(PsiElement changedElement) {
        if (changedElement instanceof PsiWhiteSpace) {
            PsiElement parent = changedElement.getParent();
            if (parent instanceof PyFile) {
                return parent;
            }
        }
        return null;
    }
}
