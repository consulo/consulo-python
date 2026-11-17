// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.psi.PyPattern;
import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.base.InstructionImpl;
import consulo.language.psi.PsiElement;

public class RefutablePatternInstruction extends InstructionImpl {
    private final boolean myMatched;

    public RefutablePatternInstruction(ControlFlowBuilder builder, PyPattern element, boolean matched) {
        super(builder, element);
        myMatched = matched;
    }

    @Override
    public PsiElement getElement() {
        PsiElement element = super.getElement();
        assert element != null;
        return element;
    }

    public boolean isMatched() {
        return myMatched;
    }

    @Override
    public String getElementPresentation() {
        return (myMatched ? "matched" : "refutable") + " pattern: " + getElement().getText();
    }
}
