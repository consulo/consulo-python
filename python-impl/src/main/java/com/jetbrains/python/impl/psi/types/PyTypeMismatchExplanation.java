// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A node in the tree explaining <i>why</i> a type mismatch was reported.
 * <p>
 * The tree is produced on demand by {@link PyTypeChecker#explainMismatch} only when a type error is about
 * to be reported (it is never gathered during normal matching). Each node carries a single,
 * already-localized line as a {@link ProblemMessage} — a plain-text {@code ProblemMessage.description} for tests
 * and the Problems view, paired with a rich {@code ProblemMessage.tooltip} in which type and class
 * references are highlighted and clickable. {@link #getChildren()} hold the nested reasons, e.g.
 *
 * <pre>
 * "C" is incompatible with protocol "A"      <- root
 *   "a" is an incompatible type              <- child
 *     "str" is not assignable to "int"       <- grandchild (leaf)
 * </pre>
 * <p>
 * {@link #getStep()}, when present, is a structured description of <i>what kind</i> of edge this node is (a protocol
 * check, an attribute or type-argument descent, a nominal leaf, a variance failure, …). It carries the
 * already-codified pieces the failure is about, so a renderer can recompose them into prose — folding a
 * chain of single-child descents into one access-path sentence, and adding the educational clause a
 * variance failure needs — instead of showing one indented line per node. It is purely presentational
 * metadata: {@link #getMessage()} remains the source of truth, and any node without a step still renders from it.
 */
public class PyTypeMismatchExplanation {
    private final ProblemMessage message;
    private final List<PyTypeMismatchExplanation> children;
    private final @Nullable PyMismatchStep step;

    public PyTypeMismatchExplanation(ProblemMessage message, List<PyTypeMismatchExplanation> children, @Nullable PyMismatchStep step) {
        this.message = message;
        this.children = children;
        this.step = step;
    }

    public PyTypeMismatchExplanation(ProblemMessage message, List<PyTypeMismatchExplanation> children) {
        this(message, children, null);
    }

    public PyTypeMismatchExplanation(ProblemMessage message) {
        this(message, Collections.emptyList());
    }

    public ProblemMessage getMessage() {
        return message;
    }

    public List<PyTypeMismatchExplanation> getChildren() {
        return children;
    }

    public @Nullable PyMismatchStep getStep() {
        return step;
    }
}
