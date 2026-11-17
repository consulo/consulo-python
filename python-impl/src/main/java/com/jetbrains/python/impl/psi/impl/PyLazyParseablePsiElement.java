// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;

import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.impl.psi.LazyParseablePsiElement;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.impl.psi.PyUtil;
import org.jspecify.annotations.Nullable;

public class PyLazyParseablePsiElement extends LazyParseablePsiElement implements PyElement {

    public PyLazyParseablePsiElement(IElementType type,
                                     @Nullable CharSequence buffer) {
        super(type, buffer);
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(this));
        if (visitor instanceof PyElementVisitor) {
            acceptPyVisitor(((PyElementVisitor)visitor));
        }
        else {
            super.accept(visitor);
        }
    }

    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyElement(this);
    }

    @Override
    public PythonLanguage getLanguage() {
        return (PythonLanguage)PythonFileType.INSTANCE.getLanguage();
    }

    @Override
    public PsiReference findReferenceAt(int offset) {
        return PyBaseElementImpl.findReferenceAt(this, offset);
    }
}
