// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiRecursiveVisitor;

public class PyRecursiveElementVisitor extends PyElementVisitor implements PsiRecursiveVisitor {
    @Override
    public void visitElement(PsiElement element) {
        element.acceptChildren(this);
    }
}
