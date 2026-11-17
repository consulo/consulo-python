package com.jetbrains.python.ast;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiRecursiveVisitor;

public class PyAstRecursiveElementVisitor extends PyAstElementVisitor implements PsiRecursiveVisitor {
    @Override
    public void visitElement(PsiElement element) {
        element.acceptChildren(this);
    }
}
