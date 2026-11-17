// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.base.InstructionImpl;

public class PyUnreachableInstruction extends InstructionImpl {
    private final boolean myIsUnreachableForTypeChecking;

    public PyUnreachableInstruction(ControlFlowBuilder builder, boolean isUnreachableForTypeChecking) {
        super(builder, null);
        myIsUnreachableForTypeChecking = isUnreachableForTypeChecking;
    }

    public boolean isUnreachableForTypeChecking() {
        return myIsUnreachableForTypeChecking;
    }

    @Override
    public String getElementPresentation() {
        return myIsUnreachableForTypeChecking ? "Unreachable for type checking" : "Unreachable";
    }
}
