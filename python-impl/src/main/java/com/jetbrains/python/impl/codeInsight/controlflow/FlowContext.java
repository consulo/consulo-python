// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Declared in JB {@code PyDataFlow.kt}.
 * <p>
 * {@code evaluateConditionTypes} - whether to treat a branch as unreachable when the static type of its condition
 * guarantees the opposite truthiness (e.g. the {@code then} branch of {@code if a:} where {@code a} is always falsy because its
 * {@code __bool__} returns a false literal).
 * Enabled only for inspection-facing reachability to keep the extra type evaluation out of hot paths like
 * reaching-definitions analysis.
 */
public final class FlowContext {
    private final TypeEvalContext myTypeEvalContext;
    private final boolean myCheckNoReturnCalls;
    private final boolean myEvaluateConditionTypes;

    public FlowContext(TypeEvalContext typeEvalContext, boolean checkNoReturnCalls) {
        this(typeEvalContext, checkNoReturnCalls, false);
    }

    public FlowContext(TypeEvalContext typeEvalContext, boolean checkNoReturnCalls, boolean evaluateConditionTypes) {
        myTypeEvalContext = typeEvalContext;
        myCheckNoReturnCalls = checkNoReturnCalls;
        myEvaluateConditionTypes = evaluateConditionTypes;
    }

    public TypeEvalContext getTypeEvalContext() {
        return myTypeEvalContext;
    }

    public boolean getCheckNoReturnCalls() {
        return myCheckNoReturnCalls;
    }

    public boolean getEvaluateConditionTypes() {
        return myEvaluateConditionTypes;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlowContext that)) {
            return false;
        }
        return myCheckNoReturnCalls == that.myCheckNoReturnCalls &&
            myEvaluateConditionTypes == that.myEvaluateConditionTypes &&
            myTypeEvalContext.equals(that.myTypeEvalContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myTypeEvalContext, myCheckNoReturnCalls, myEvaluateConditionTypes);
    }

    @Override
    public String toString() {
        return "FlowContext(typeEvalContext=" + myTypeEvalContext +
            ", checkNoReturnCalls=" + myCheckNoReturnCalls +
            ", evaluateConditionTypes=" + myEvaluateConditionTypes + ")";
    }
}
