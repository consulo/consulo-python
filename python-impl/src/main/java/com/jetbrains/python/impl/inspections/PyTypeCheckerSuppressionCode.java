// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

/**
 * User-facing suppression codes for {@link PyTypeCheckerInspection}.
 * <p>
 * Each problem reported by the inspection is tagged with one of these codes, so a user can silence a single
 * category with a {@code # noinspection <id>} comment (e.g. {@code # noinspection bad-return}) instead of the broad,
 * redundant {@code # noinspection PyTypeChecker}, which keeps working as a catch-all for every code.
 * <p>
 * The kebab-case {@link #getId()}s follow the conventions of the newest Python type checkers (pyrefly / ty), e.g.
 * {@code bad-argument-type}, {@code bad-return}, {@code not-iterable}.
 */
public enum PyTypeCheckerSuppressionCode {
    /**
     * Call-site argument type mismatches: regular calls, {@code self}/{@code cls}, {@code ParamSpec}, protocols, {@code **TypedDict} arguments.
     */
    BAD_ARGUMENT_TYPE("bad-argument-type"),

    /**
     * Return / yield mismatches: {@code return}, implicit returns, generator return type, {@code yield}, {@code __init__} must return {@code None}.
     */
    BAD_RETURN("bad-return"),

    /**
     * Assignment value mismatches: annotated targets, attributes, descriptor {@code __set__}, augmented assignment, default values, enum members.
     */
    BAD_ASSIGNMENT("bad-assignment"),

    /**
     * Tuple unpacking count balance: too many / not enough values, more than one starred target.
     */
    BAD_UNPACKING("bad-unpacking"),

    /**
     * Binary / augmented-assignment operator operand type mismatches.
     */
    UNSUPPORTED_OPERATOR("unsupported-operator"),

    /**
     * Subscription key mismatches and out-of-range tuple indexing.
     */
    BAD_INDEX("bad-index"),

    /**
     * A value used where an iterable is required: {@code for}, comprehensions, {@code *} unpacking.
     */
    NOT_ITERABLE("not-iterable"),

    /**
     * A value used where a mapping is required: {@code **} unpacking.
     */
    NOT_MAPPING("not-mapping"),

    /**
     * A value used in a {@code with}/{@code async with} that is not a (async) context manager.
     */
    BAD_CONTEXT_MANAGER("bad-context-manager"),

    /**
     * TypedDict value type errors and missing required keys.
     */
    BAD_TYPED_DICT("bad-typed-dict"),

    /**
     * TypedDict unknown/extra keys.
     */
    BAD_TYPED_DICT_KEY("bad-typed-dict-key");

    private final String id;

    PyTypeCheckerSuppressionCode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
