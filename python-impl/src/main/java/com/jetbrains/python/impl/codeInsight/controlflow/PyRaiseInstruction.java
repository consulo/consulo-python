// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.psi.PyAssertStatement;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyStatement;
import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.base.InstructionImpl;

public class PyRaiseInstruction extends InstructionImpl {
    private PyRaiseInstruction(ControlFlowBuilder builder, PyStatement raiseOrAssert) {
        super(builder, raiseOrAssert);
    }

    public PyRaiseInstruction(ControlFlowBuilder builder, PyRaiseStatement raise) {
        this(builder, (PyStatement) raise);
    }

    public PyRaiseInstruction(ControlFlowBuilder builder, PyAssertStatement assertStatement) {
        this(builder, (PyStatement) assertStatement);
    }

    @Override
    public String getElementPresentation() {
        return "raise: " + getElement();
    }
}
