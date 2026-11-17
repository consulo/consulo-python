// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import org.jspecify.annotations.Nullable;

/**
 * Structured metadata for a {@link PyTypeMismatchExplanation} node, describing the semantic role of the edge so
 * a prose renderer can recompose the failure into readable sentences (see {@link PyTypeMismatchExplanation#getStep()}).
 * <p>
 * The variants split into three groups:
 * <ul>
 *  <li><b>descents</b> ({@link Protocol}, {@link Attribute}, {@link TypeArgument}, {@link Return}) — a step <i>into</i> a nested position; a
 *    single-child chain of these folds into one access path (e.g. {@code inner.value});</li>
 *  <li><b>terminals</b> ({@link Nominal}, {@link Missing}, {@link Invariant}, {@link ContravariantParameter}) — the reason the descent
 *    bottomed out, rendered as the sentence's predicate;</li>
 *  <li><b>branches</b> ({@link NoUnionMember}) — a point where several parallel reasons apply, each kept on its own line.</li>
 * </ul>
 * <p>
 * Every type/class piece is a pre-rendered {@link CodifiedParam} (plain description + clickable-HTML tooltip), so the
 * renderer never re-resolves types: it only arranges the pieces into prose.
 */
public sealed interface PyMismatchStep {
    /**
     * The protocol-conformance frame; {@code subject} is the type whose members are being checked, {@code expected} the protocol
     * it is checked against. {@code expected} is used only to name the member in a union arm (where several protocols are
     * tried and the reader must know which one each line is about); a lone protocol mismatch ignores it (the headline
     * already names the protocol). Adds no path segment.
     */
    record Protocol(CodifiedParam subject, @Nullable CodifiedParam expected) implements PyMismatchStep {
        public Protocol(CodifiedParam subject) {
            this(subject, null);
        }
    }

    /**
     * Descent into the attribute/member named {@code name}; folds into the access path as {@code .name}. {@code actual}/{@code expected}
     * are the attribute's own types (the subclass's vs the protocol's); the renderer reports them directly when the
     * mismatch is <i>inside</i> a parameterized attribute type, rather than descending into a meaningless {@code [1][1]} of
     * type-argument positions.
     */
    record Attribute(String name, @Nullable CodifiedParam actual, @Nullable CodifiedParam expected) implements PyMismatchStep {
        public Attribute(String name) {
            this(name, null, null);
        }
    }

    /**
     * Descent into the type argument at {@code oneBasedIndex}. {@code actual}/{@code expected} are the argument's types at this level
     * (e.g. {@code set[None]} vs {@code set[int]} for the element of a {@code list}), so the renderer can drill in with a readable
     * "expected …, got …" line per level instead of a meaningless {@code [1][1]} position path.
     */
    record TypeArgument(int oneBasedIndex, @Nullable CodifiedParam actual, @Nullable CodifiedParam expected) implements PyMismatchStep {
        public TypeArgument(int oneBasedIndex) {
            this(oneBasedIndex, null, null);
        }
    }

    /**
     * Descent into a callable's return type.
     */
    final class Return implements PyMismatchStep {
        public static final Return INSTANCE = new Return();

        private Return() {
        }
    }

    /**
     * Terminal: {@code actual} is not assignable to {@code expected}.
     */
    record Nominal(CodifiedParam actual, CodifiedParam expected) implements PyMismatchStep {
    }

    /**
     * Terminal: the member named {@code name} is absent on the actual type. {@code isMethod} picks the noun — "method" for a
     * callable protocol member, "attribute" otherwise — so the message reads "has no method {@code f}" / "has no attribute {@code x}".
     */
    record Missing(String name, boolean isMethod) implements PyMismatchStep {
        public Missing(String name) {
            this(name, false);
        }
    }

    /**
     * Terminal, educational: an invariant type parameter ({@code typeVar} of {@code owner}) rejects {@code actual} where
     * {@code expected} is required. Recorded only on the "surprising" path where {@code actual} <i>is</i> a subtype of
     * {@code expected}, so the renderer can add the anti-surprise "even though … is a subtype of …" clause.
     * <p>
     * {@code readOnlyAlternative}, when set, is the name of a covariant read-only supertype the reader could annotate
     * the target with instead (e.g. {@code Sequence} for a {@code list}, {@code Mapping} for a {@code dict}), which sidesteps the
     * invariance; the renderer appends a "use {@code Sequence} if you only read from it" hint.
     */
    record Invariant(String typeVar,
                     CodifiedParam owner,
                     CodifiedParam actual,
                     CodifiedParam expected,
                     @Nullable String readOnlyAlternative) implements PyMismatchStep {
        public Invariant(String typeVar, CodifiedParam owner, CodifiedParam actual, CodifiedParam expected) {
            this(typeVar, owner, actual, expected, null);
        }
    }

    /**
     * Terminal: a TypedDict key mismatch. {@code owner} is the expected TypedDict type; {@code key} is the offending key;
     * {@code problem} says how it failed. {@code actual} and {@code expected} carry the key's value types for
     * {@link TypedDictKeyProblem#VALUE_TYPE} (null for the other kinds, which are about presence/qualifiers, not types).
     */
    record TypedDictKey(CodifiedParam owner,
                        String key,
                        TypedDictKeyProblem problem,
                        @Nullable CodifiedParam actual,
                        @Nullable CodifiedParam expected) implements PyMismatchStep {
    }

    /**
     * A transparent grouping of several independent top-level reasons (e.g. a callable that is wrong in both a
     * parameter and its return type). The renderer emits the children directly, with no line of its own — it
     * only exists so {@link PyTypeChecker#explainMismatch} can return a single root when the failure has several.
     */
    final class Combined implements PyMismatchStep {
        public static final Combined INSTANCE = new Combined();

        private Combined() {
        }
    }

    /**
     * Terminal, educational: a contravariant callable parameter is too narrow — it must accept everything the
     * caller might pass ({@code required}) but only accepts {@code provided}. Identified by {@code name} when it has one, otherwise
     * by its 1-based position {@code oneBasedIndex} (parameters of a {@code Callable[[…], …]} are positional and unnamed).
     */
    record ContravariantParameter(@Nullable String name,
                                  @Nullable Integer oneBasedIndex,
                                  CodifiedParam required,
                                  CodifiedParam provided) implements PyMismatchStep {
    }

    /**
     * Branch: {@code actual} matches no member of the union/protocol set {@code union}; each member's reason is a child.
     * <p>
     * {@code actualIsUnion} tells the two directions this frame is recorded in apart. When {@code false} (the default), the
     * REQUIRED side is the union — {@code actual} (a single type) is assignable to no member of {@code union} — so a call-site
     * renderer reads "needs {@code x} to match {@code A | B}, but {@code arg} is {@code C}, which matches no member". When {@code true}, it is the
     * PROVIDED side that is a union — {@code actual} is the union value, {@code union} is the single required type each member
     * failed against — so "which matches no member" (of a non-union required type) would be nonsense; the renderer
     * must instead name the required type plainly and list the failing members as arms.
     */
    record NoUnionMember(CodifiedParam actual, CodifiedParam union, boolean actualIsUnion) implements PyMismatchStep {
        public NoUnionMember(CodifiedParam actual, CodifiedParam union) {
            this(actual, union, false);
        }
    }

    /**
     * A per-member wrapper under a {@link NoUnionMember} branch, naming the failing union {@code member} so several
     * otherwise-indistinguishable reasons can be told apart (e.g. two callables that differ only by a parameter name).
     * The prose renderer drops this wrapper and keeps a single member-named arm when the wrapped reason already
     * self-names (a missing/wrong-typed protocol attribute), and keeps the wrapper line above the reason otherwise.
     */
    record UnionMember(CodifiedParam member) implements PyMismatchStep {
    }

    /**
     * An intersection branch. Its quantifier is the opposite of a union's, so the two cannot share a step.
     * <p>
     * {@code expectedIsIntersection} tells the directions apart. When {@code true}, the required side is the intersection:
     * {@code other} failed against one or more members of {@code intersection}, each an independent requirement. When
     * {@code false}, the provided value is the intersection: no member of it is assignable to the required {@code other}.
     */
    record NoIntersectionMember(CodifiedParam other, CodifiedParam intersection, boolean expectedIsIntersection) implements PyMismatchStep {
        public NoIntersectionMember(CodifiedParam other, CodifiedParam intersection) {
            this(other, intersection, false);
        }
    }

    /**
     * {@link UnionMember} for an intersection branch: names the failing {@code member} to tell parallel reasons apart.
     */
    record IntersectionMember(CodifiedParam member) implements PyMismatchStep {
    }
}
