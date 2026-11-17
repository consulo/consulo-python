// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.PyExpression;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class ParametersAccumulator {
    private @Nullable Boolean init = null;
    private @Nullable Boolean repr = null;
    private @Nullable Boolean eq = null;
    private @Nullable Boolean order = null;
    private @Nullable Boolean unsafeHash = null;
    // PEP 681: A class that has been decorated with dataclass_transform is considered neither frozen nor non-frozen
    // Spec: https://typing.python.org/en/latest/spec/dataclasses.html#dataclass-semantics
    private @Nullable Boolean frozen = null;
    private @Nullable Boolean matchArgs = null;
    private @Nullable Boolean kwOnly = null;
    private @Nullable Boolean slots = null;

    private @Nullable PyExpression initArgument = null;
    private @Nullable PyExpression reprArgument = null;
    private @Nullable PyExpression eqArgument = null;
    private @Nullable PyExpression orderArgument = null;
    private @Nullable PyExpression unsafeHashArgument = null;
    private @Nullable PyExpression frozenArgument = null;
    private @Nullable PyExpression matchArgsArgument = null;
    private @Nullable PyExpression kwOnlyArgument = null;
    private @Nullable PyExpression slotsArgument = null;

    private final Map<String, PyExpression> frameworkSpecificArguments = new LinkedHashMap<>();

    public @Nullable Boolean getInit() {
        return init;
    }

    public void setInit(@Nullable Boolean init) {
        this.init = init;
    }

    public @Nullable Boolean getRepr() {
        return repr;
    }

    public void setRepr(@Nullable Boolean repr) {
        this.repr = repr;
    }

    public @Nullable Boolean getEq() {
        return eq;
    }

    public void setEq(@Nullable Boolean eq) {
        this.eq = eq;
    }

    public @Nullable Boolean getOrder() {
        return order;
    }

    public void setOrder(@Nullable Boolean order) {
        this.order = order;
    }

    public @Nullable Boolean getUnsafeHash() {
        return unsafeHash;
    }

    public void setUnsafeHash(@Nullable Boolean unsafeHash) {
        this.unsafeHash = unsafeHash;
    }

    public @Nullable Boolean getFrozen() {
        return frozen;
    }

    public void setFrozen(@Nullable Boolean frozen) {
        this.frozen = frozen;
    }

    public @Nullable Boolean getMatchArgs() {
        return matchArgs;
    }

    public void setMatchArgs(@Nullable Boolean matchArgs) {
        this.matchArgs = matchArgs;
    }

    public @Nullable Boolean getKwOnly() {
        return kwOnly;
    }

    public void setKwOnly(@Nullable Boolean kwOnly) {
        this.kwOnly = kwOnly;
    }

    public @Nullable Boolean getSlots() {
        return slots;
    }

    public void setSlots(@Nullable Boolean slots) {
        this.slots = slots;
    }

    public @Nullable PyExpression getInitArgument() {
        return initArgument;
    }

    public void setInitArgument(@Nullable PyExpression initArgument) {
        this.initArgument = initArgument;
    }

    public @Nullable PyExpression getReprArgument() {
        return reprArgument;
    }

    public void setReprArgument(@Nullable PyExpression reprArgument) {
        this.reprArgument = reprArgument;
    }

    public @Nullable PyExpression getEqArgument() {
        return eqArgument;
    }

    public void setEqArgument(@Nullable PyExpression eqArgument) {
        this.eqArgument = eqArgument;
    }

    public @Nullable PyExpression getOrderArgument() {
        return orderArgument;
    }

    public void setOrderArgument(@Nullable PyExpression orderArgument) {
        this.orderArgument = orderArgument;
    }

    public @Nullable PyExpression getUnsafeHashArgument() {
        return unsafeHashArgument;
    }

    public void setUnsafeHashArgument(@Nullable PyExpression unsafeHashArgument) {
        this.unsafeHashArgument = unsafeHashArgument;
    }

    public @Nullable PyExpression getFrozenArgument() {
        return frozenArgument;
    }

    public void setFrozenArgument(@Nullable PyExpression frozenArgument) {
        this.frozenArgument = frozenArgument;
    }

    public @Nullable PyExpression getMatchArgsArgument() {
        return matchArgsArgument;
    }

    public void setMatchArgsArgument(@Nullable PyExpression matchArgsArgument) {
        this.matchArgsArgument = matchArgsArgument;
    }

    public @Nullable PyExpression getKwOnlyArgument() {
        return kwOnlyArgument;
    }

    public void setKwOnlyArgument(@Nullable PyExpression kwOnlyArgument) {
        this.kwOnlyArgument = kwOnlyArgument;
    }

    public @Nullable PyExpression getSlotsArgument() {
        return slotsArgument;
    }

    public void setSlotsArgument(@Nullable PyExpression slotsArgument) {
        this.slotsArgument = slotsArgument;
    }

    public Map<String, PyExpression> getFrameworkSpecificArguments() {
        return frameworkSpecificArguments;
    }

    /**
     * The dataclass parameters every framework shares. Returns {@code true} when {@code name} was handled.
     */
    public boolean updateCommonParameter(
        String name,
        @Nullable PyExpression value,
        @Nullable PyExpression argument
    ) {
        switch (name) {
            case "init": {
                init = PyEvaluator.evaluateAsBooleanNoResolve(value);
                initArgument = argument;
                break;
            }
            case "repr": {
                repr = PyEvaluator.evaluateAsBooleanNoResolve(value);
                reprArgument = argument;
                break;
            }
            case "frozen": {
                frozen = PyEvaluator.evaluateAsBooleanNoResolve(value);
                frozenArgument = argument;
                break;
            }
            case "match_args": {
                matchArgs = PyEvaluator.evaluateAsBooleanNoResolve(value);
                matchArgsArgument = argument;
                break;
            }
            case "kw_only": {
                kwOnly = PyEvaluator.evaluateAsBooleanNoResolve(value);
                kwOnlyArgument = argument;
                break;
            }
            case "slots": {
                slots = PyEvaluator.evaluateAsBooleanNoResolve(value);
                slotsArgument = argument;
                break;
            }
            default:
                return false;
        }
        return true;
    }
}
