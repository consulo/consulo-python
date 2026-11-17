// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNamedElement;
import consulo.language.impl.psi.LightElement;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyElement;

public class LightNamedElement extends LightElement implements PyElement, PsiNamedElement {
    protected final String myName;

    public LightNamedElement(PsiManager manager, Language language, String name) {
        super(manager, language);
        myName = name;
    }

    @Override
    public String getText() {
        return myName;
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        super.accept(visitor);
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        throw new UnsupportedOperationException("LightNamedElement#setName() is not supported");
    }

    @Override
    public String toString() {
        return "LightNamedElement(" + myName + ")";
    }
}
