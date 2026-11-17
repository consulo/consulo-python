// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.PyExpression;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Declared in JB {@code PyDataclasses.kt}.
 */
public final class DataclassParameterArgumentMapping {
    private final @Nullable PyExpression myInitArgument;
    private final @Nullable PyExpression myReprArgument;
    private final @Nullable PyExpression myEqArgument;
    private final @Nullable PyExpression myOrderArgument;
    private final @Nullable PyExpression myUnsafeHashArgument;
    private final @Nullable PyExpression myFrozenArgument;
    private final @Nullable PyExpression myMatchArgsArgument;
    private final @Nullable PyExpression myKwOnlyArgument;
    private final @Nullable PyExpression mySlotsArgument;
    private Map<String, PyExpression> myOthers;

    public DataclassParameterArgumentMapping(@Nullable PyExpression initArgument,
                                             @Nullable PyExpression reprArgument,
                                             @Nullable PyExpression eqArgument,
                                             @Nullable PyExpression orderArgument,
                                             @Nullable PyExpression unsafeHashArgument,
                                             @Nullable PyExpression frozenArgument,
                                             @Nullable PyExpression matchArgsArgument,
                                             @Nullable PyExpression kwOnlyArgument,
                                             @Nullable PyExpression slotsArgument,
                                             Map<String, PyExpression> others) {
        myInitArgument = initArgument;
        myReprArgument = reprArgument;
        myEqArgument = eqArgument;
        myOrderArgument = orderArgument;
        myUnsafeHashArgument = unsafeHashArgument;
        myFrozenArgument = frozenArgument;
        myMatchArgsArgument = matchArgsArgument;
        myKwOnlyArgument = kwOnlyArgument;
        mySlotsArgument = slotsArgument;
        myOthers = others;
    }

    public @Nullable PyExpression getInitArgument() {
        return myInitArgument;
    }

    public @Nullable PyExpression getReprArgument() {
        return myReprArgument;
    }

    public @Nullable PyExpression getEqArgument() {
        return myEqArgument;
    }

    public @Nullable PyExpression getOrderArgument() {
        return myOrderArgument;
    }

    public @Nullable PyExpression getUnsafeHashArgument() {
        return myUnsafeHashArgument;
    }

    public @Nullable PyExpression getFrozenArgument() {
        return myFrozenArgument;
    }

    public @Nullable PyExpression getMatchArgsArgument() {
        return myMatchArgsArgument;
    }

    public @Nullable PyExpression getKwOnlyArgument() {
        return myKwOnlyArgument;
    }

    public @Nullable PyExpression getSlotsArgument() {
        return mySlotsArgument;
    }

    public Map<String, PyExpression> getOthers() {
        return myOthers;
    }

    public void setOthers(Map<String, PyExpression> others) {
        myOthers = others;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataclassParameterArgumentMapping that)) {
            return false;
        }
        return Objects.equals(myInitArgument, that.myInitArgument) &&
            Objects.equals(myReprArgument, that.myReprArgument) &&
            Objects.equals(myEqArgument, that.myEqArgument) &&
            Objects.equals(myOrderArgument, that.myOrderArgument) &&
            Objects.equals(myUnsafeHashArgument, that.myUnsafeHashArgument) &&
            Objects.equals(myFrozenArgument, that.myFrozenArgument) &&
            Objects.equals(myMatchArgsArgument, that.myMatchArgsArgument) &&
            Objects.equals(myKwOnlyArgument, that.myKwOnlyArgument) &&
            Objects.equals(mySlotsArgument, that.mySlotsArgument) &&
            myOthers.equals(that.myOthers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myInitArgument, myReprArgument, myEqArgument, myOrderArgument, myUnsafeHashArgument, myFrozenArgument,
            myMatchArgsArgument, myKwOnlyArgument, mySlotsArgument, myOthers);
    }
}
