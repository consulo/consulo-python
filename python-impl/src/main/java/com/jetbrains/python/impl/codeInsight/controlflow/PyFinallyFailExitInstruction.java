// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.Instruction;
import consulo.language.controlFlow.base.InstructionImpl;

/**
 * It is an implicit raise at the end of a finally block when we got here due to exception propagation
 * or return statement in try-except-else parts.
 */
public class PyFinallyFailExitInstruction extends InstructionImpl {
    private final Instruction myBegin;

    public PyFinallyFailExitInstruction(ControlFlowBuilder builder, Instruction begin) {
        super(builder, begin.getElement());
        myBegin = begin;
    }

    public Instruction getBegin() {
        return myBegin;
    }

    @Override
    public String getElementPresentation() {
        return "finally fail exit";
    }
}
