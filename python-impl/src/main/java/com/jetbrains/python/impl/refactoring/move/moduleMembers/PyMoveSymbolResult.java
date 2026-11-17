// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.moduleMembers;

import consulo.language.psi.PsiFile;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyMoveSymbolResult {
    private final List<PsiFile> myOptimizeImportsTargets;

    public PyMoveSymbolResult(List<PsiFile> optimizeImportTargets) {
        myOptimizeImportsTargets = optimizeImportTargets;
    }

    public List<PsiFile> getOptimizeImportsTargets() {
        return myOptimizeImportsTargets;
    }
}
