// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.types.PyMismatchStep;
import com.jetbrains.python.impl.psi.types.PyTypeMismatchExplanation;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link PyTypeMismatchExplanation} tree as <b>prose</b> rather than the raw one-line-per-node indented list.
 * <p>
 * The tree that {@link com.jetbrains.python.impl.psi.types.PyTypeChecker#explainMismatch} produces is mostly a <i>linear
 * chain</i> of single-child "descent" frames (a protocol check, then the offending attribute, then its type, …)
 * bottoming out in one terminal reason. Showing one indented line per frame forces the reader to walk the whole
 * chain; instead this renderer <b>folds each such chain into one sentence</b> whose subject is the type that failed
 * and whose object is an access path ({@code inner.value}) into it — so arbitrary nesting depth becomes path length,
 * not indentation. Indentation survives only at genuine <i>branches</i> (a union tried against each member), where
 * there really are several parallel reasons to keep apart.
 * <p>
 * Two failure kinds also carry an <b>educational</b> clause the raw tree can't: an invariant type parameter
 * rejecting a subtype (why a {@code list} of {@code bool} isn't a {@code list} of {@code int} <i>even though</i> {@code bool} is a subtype of
 * {@code int}), and a contravariant callable parameter that is too narrow. Those come from the node's {@link PyMismatchStep}.
 * <p>
 * When the mismatch is a <b>call argument</b> ({@link CallSite} supplied), the breakdown is reframed as a two-line
 * requirement/actual sentence that names the callee, the parameter and the argument expression, e.g.
 * <pre>
 * `f()` needs parameter `items` of type `Iterable[str]` to have attribute `__iter__`
 * `xs()` is `Page`, which has no `__iter__`
 * </pre>
 * This replaces the "Expected type …, got …" headline for the recognized shapes; anything else falls back to
 * that headline plus the plain folded breakdown.
 * <p>
 * Every node keeps its localized {@link PyTypeMismatchExplanation#getMessage()} as the source of truth: a node whose
 * {@link PyMismatchStep} is missing or whose shape this renderer doesn't recognize falls back to that message
 * verbatim (and its children are recursed), so the prose is never <i>worse</i> than the plain tree, only better
 * where structure is known.
 */
public final class PyTypeMismatchProse {
    private PyTypeMismatchProse() {
    }

    /**
     * One rendered prose line: its nesting {@code depth} (0 at the top; only branches go deeper) and its {@code message}.
     */
    public static final class ProseLine {
        private final int depth;
        private final ProblemMessage message;

        public ProseLine(int depth, ProblemMessage message) {
            this.depth = depth;
            this.message = message;
        }

        public int getDepth() {
            return depth;
        }

        public ProblemMessage getMessage() {
            return message;
        }
    }

    /**
     * The call-site context for an argument mismatch, used to reframe the breakdown as a single requirement/actual
     * sentence. {@code callee} is the called function rendered as a {@code name()} span, clickable when it is a named
     * function/method ({@code null} for an anonymous callee, which disables the reframing); {@code argument} is the (shortened)
     * argument expression text; {@code parameterName} is the target parameter's name ({@code null} for a positional-only slot);
     * {@code parameterType} and {@code actualType} are the expected and actual argument types, pre-rendered as clickable spans.
     * {@code actualIsLiteral} is set when the argument is a literal (e.g. {@code "a"}, {@code 42}), whose value the argument expression
     * already shows — so the "…, but `arg` is `Literal[…]`" clause would only restate it and is dropped.
     */
    public static final class CallSite {
        private final @Nullable CodifiedParam callee;
        private final String argument;
        private final @Nullable String parameterName;
        private final CodifiedParam parameterType;
        private final CodifiedParam actualType;
        private final boolean actualIsLiteral;

        public CallSite(@Nullable CodifiedParam callee,
                        String argument,
                        @Nullable String parameterName,
                        CodifiedParam parameterType,
                        CodifiedParam actualType,
                        boolean actualIsLiteral) {
            this.callee = callee;
            this.argument = argument;
            this.parameterName = parameterName;
            this.parameterType = parameterType;
            this.actualType = actualType;
            this.actualIsLiteral = actualIsLiteral;
        }

        public CallSite(@Nullable CodifiedParam callee,
                        String argument,
                        @Nullable String parameterName,
                        CodifiedParam parameterType,
                        CodifiedParam actualType) {
            this(callee, argument, parameterName, parameterType, actualType, false);
        }

        public @Nullable CodifiedParam getCallee() {
            return callee;
        }

        public String getArgument() {
            return argument;
        }

        public @Nullable String getParameterName() {
            return parameterName;
        }

        public CodifiedParam getParameterType() {
            return parameterType;
        }

        public CodifiedParam getActualType() {
            return actualType;
        }

        public boolean getActualIsLiteral() {
            return actualIsLiteral;
        }
    }

    /**
     * Flattens {@code roots} into prose lines. Exposed for tests, which assert on the plain {@link ProblemMessage#description()}.
     */
    public static List<ProseLine> proseLines(List<PyTypeMismatchExplanation> roots) {
        List<ProseLine> out = new ArrayList<>();
        for (PyTypeMismatchExplanation root : roots) {
            emit(root, 0, out);
        }
        return out;
    }

    /**
     * The call-site framing for {@code root}, or {@code null} only when {@code callSite} lacks a callee. A few shapes get a tailored
     * two-line requirement/actual sentence (a missing attribute, a wrong attribute type, a plain nominal, a union);
     * every other shape (a variance failure, a callable wrong in a parameter or return, a TypedDict key, …) gets a
     * generic "`f()` needs parameter `a` of type `T`" / "`x` is `Y`" headline followed by the folded breakdown
     * beneath it — so the reader always sees the call-site framing, and never the bare "Expected type …" headline.
     * Exposed for tests.
     */
    public static @Nullable List<ProseLine> argumentProseLines(CallSite callSite, PyTypeMismatchExplanation root) {
        if (callSite.callee == null) {
            return null;
        }

        // A union. Two directions, told apart by [NoUnionMember.actualIsUnion]:
        //  - REQUIRED side is the union (a single argument against a union parameter): "f() needs `x` to match `A | B`,
        //    but `arg` is `C`, which matches no member", then one line per member that failed for a *structural* reason.
        //    A member the argument is trivially not (a bare "None is not assignable to int") adds nothing, so it's dropped.
        //  - PROVIDED value is the union (a union argument against a single parameter): the required type is NOT a union,
        //    so "which matches no member" would be nonsense; name the required type plainly ("f() needs `x` of type `T`,
        //    but `arg` is `A | B`") and list the failing members as arms.
        PyMismatchStep step = root.getStep();
        if (step instanceof PyMismatchStep.NoUnionMember noUnionMember) {
            ProblemMessage requirement = noUnionMember.actualIsUnion() ? requirementNominal(callSite) : unionRequirement(callSite, noUnionMember.union());
            ProblemMessage actual = noUnionMember.actualIsUnion() ? actualNominal(callSite) : actualUnion(callSite);
            List<ProseLine> out = new ArrayList<>();
            out.add(new ProseLine(0, oneLine(callSite, requirement, actual)));
            emitUnionArms(root, 1, out);
            return out;
        }

        if (foldChain(root) instanceof Fold.Terminal chain) {
            PyMismatchStep terminal = chain.step;
            if (terminal instanceof PyMismatchStep.Missing missing) {
                if (chain.subject != null) {
                    return List.of(new ProseLine(0, oneLine(callSite, requirementMissing(callSite, missing.name()), actualMissing(callSite, missing.name()))));
                }
            }
            else if (terminal instanceof PyMismatchStep.Nominal nominal) {
                if (chain.subject != null && !chain.path.isEmpty()) {
                    return List.of(new ProseLine(0, oneLine(callSite, requirementAttribute(callSite, chain.path.render(), nominal.expected()),
                                                            actualAttribute(callSite, chain.path.render(), nominal.actual()))));
                }
                else if (chain.subject == null && chain.path.isEmpty()) {
                    return List.of(new ProseLine(0, oneLine(callSite, requirementNominal(callSite), actualNominal(callSite))));
                }
                // a type-argument position on a bare argument: fall through to the generic framing
            }
            // a variance / TypedDict / other terminal: fall through to the generic framing
        }

        // Generic framing: the call-site requirement/actual sentence, then the folded breakdown one level deeper. This is
        // where the "prepend the call-site line" for contravariance, invariance and TypedDicts happens: the educational
        // or structural reason keeps its own line(s), but now under a headline that names the callee and argument.
        List<ProseLine> out = new ArrayList<>();
        out.add(new ProseLine(0, oneLine(callSite, requirementNominal(callSite), actualNominal(callSite))));
        for (ProseLine line : proseLines(List.of(root))) {
            out.add(new ProseLine(line.depth + 1, line.message));
        }
        return out;
    }

    /**
     * Appends {@code lines} to {@code builder} as {@code <br>}-separated tooltip fragments, prefixed with directory-tree guide lines so
     * a dedent is easy to trace back to the parent it lines up under. A line at a genuine <i>branch</i> (a parent with two
     * or more children — a callable wrong in both a parameter and its return, a union with several structural members)
     * gets a {@code ├─}/{@code └─} connector, with a {@code │} continuation drawn down through every ancestor that still has a sibling
     * below; a single-child "because" descent stays plain indentation, so the common linear chain reads as prose.
     * {@code leadingBreak} adds a break before the first line so it sits below a preceding headline.
     * <p>
     * The guide column is rendered a little larger than the text so the box-drawing glyphs fill the line height and the
     * {@code │} segments meet across rows (at the text size they leave a vertical gap in the Swing tooltip).
     */
    public static void appendLines(HtmlBuilder builder, List<ProseLine> lines, boolean leadingBreak) {
        for (int index = 0; index < lines.size(); index++) {
            ProseLine line = lines.get(index);
            if (leadingBreak || index > 0) {
                builder.br();
            }
            String prefix = treePrefix(lines, index);
            if (!prefix.isEmpty()) {
                builder.append(HtmlChunk.span().style(GUIDE_STYLE).addRaw(prefix));
            }
            builder.appendRaw(PyInspectionMessages.tooltipFragment(line.message));
        }
    }

    // Monospace so the columns align, and enlarged so the box glyphs' full-height verticals meet between rows.
    private static final String GUIDE_STYLE = "font-family:monospace;font-size:130%";

    // Directory-tree guide columns, each three cells wide so connectors, continuations and blanks align.
    private static final String CONNECTOR_MID = "├─&nbsp;";        // a child that has a sibling below it
    private static final String CONNECTOR_END = "└─&nbsp;";        // the last child in its group
    private static final String CONTINUATION = "│&nbsp;&nbsp;";    // an ancestor whose subtree is still open
    private static final String BLANK = "&nbsp;&nbsp;&nbsp;";      // a closed ancestor, or a single-child (prose) indent

    /**
     * The guide-line prefix for the line at {@code index}: one column per depth level, drawn only where there is a real
     * branch. Depth 0 (a root / the requirement-actual headline pair) gets no prefix; a level whose node is an only
     * child gets a blank column (plain indentation), so a purely linear chain renders with no connectors.
     */
    private static String treePrefix(List<ProseLine> lines, int index) {
        int depth = lines.get(index).depth;
        if (depth == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int level = 1; level < depth; level++) {
            int ancestor = ancestorAtDepth(lines, index, level);
            sb.append(ancestor >= 0 && hasFollowingSibling(lines, ancestor) ? CONTINUATION : BLANK);
        }
        if (hasFollowingSibling(lines, index)) {
            sb.append(CONNECTOR_MID);
        }
        else if (hasPrecedingSibling(lines, index)) {
            sb.append(CONNECTOR_END);
        }
        else {
            sb.append(BLANK);
        }
        return sb.toString();
    }

    /**
     * The nearest earlier line at exactly {@code level} on the path to {@code index} (its ancestor), or -1 if there is none.
     */
    private static int ancestorAtDepth(List<ProseLine> lines, int index, int level) {
        for (int j = index - 1; j >= 0; j--) {
            int d = lines.get(j).depth;
            if (d == level) {
                return j;
            }
            if (d < level) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether the line at {@code index} has a later sibling (a line at the same depth before the group closes).
     */
    private static boolean hasFollowingSibling(List<ProseLine> lines, int index) {
        int depth = lines.get(index).depth;
        for (int j = index + 1; j < lines.size(); j++) {
            int d = lines.get(j).depth;
            if (d < depth) {
                return false;
            }
            if (d == depth) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the line at {@code index} has an earlier sibling (a line at the same depth within the same group).
     */
    private static boolean hasPrecedingSibling(List<ProseLine> lines, int index) {
        int depth = lines.get(index).depth;
        for (int j = index - 1; j >= 0; j--) {
            int d = lines.get(j).depth;
            if (d < depth) {
                return false;
            }
            if (d == depth) {
                return true;
            }
        }
        return false;
    }

    private static void emit(PyTypeMismatchExplanation node, int depth, List<ProseLine> out) {
        PyMismatchStep step = node.getStep();
        // A transparent grouping of several independent top-level reasons: emit the children at the same depth, so
        // e.g. a callable wrong in both a parameter and its return type shows both lines with no wrapper above them.
        if (step instanceof PyMismatchStep.Combined) {
            for (PyTypeMismatchExplanation child : node.getChildren()) {
                emit(child, depth, out);
            }
        }
        // A contravariant callable parameter is a *frame*, not a terminal: state "is called with X, but only accepts Y",
        // then recurse the nested reason X and Y are incompatible (why `A` isn't a `B`). That nested reason is the
        // whole point — dropping it (as a terminal would) leaves the reader with no explanation.
        else if (step instanceof PyMismatchStep.ContravariantParameter contravariantParameter) {
            out.add(new ProseLine(depth, contravariant(contravariantParameter)));
            for (PyTypeMismatchExplanation child : nonTrivialReasons(node)) {
                emit(child, depth + 1, out);
            }
        }
        // A union: the "not assignable to any member" header, then one member-named line per structural failure.
        // An intersection branch has the same shape, only a different quantifier in its header.
        else if (step instanceof PyMismatchStep.NoUnionMember || step instanceof PyMismatchStep.NoIntersectionMember) {
            out.add(new ProseLine(depth, node.getMessage()));
            emitUnionArms(node, depth + 1, out);
        }
        // A protocol checked against several members: when MORE THAN ONE fails, describe every failure as an arm that
        // still carries the subject (`Attribute 'x' of 'A' …` / `'A' has no 'y'`) — not just the first. A single
        // failure keeps the folded one-liner (`Attribute 'x' of 'A' is 'str', but 'int' was expected`).
        else if (step instanceof PyMismatchStep.Protocol protocol) {
            if (node.getChildren().size() >= 2) {
                out.add(new ProseLine(depth, node.getMessage()));
                for (PyTypeMismatchExplanation child : node.getChildren()) {
                    emitFolded(child, depth + 1, out, protocol.subject());
                }
            }
            else {
                emitFolded(node, depth, out, null);
            }
        }
        else {
            emitFolded(node, depth, out, null);
        }
    }

    /**
     * Emits {@code node} by folding its single-child descent chain into one line (a terminal reason, drilling into any
     * nested type arguments; or a self-deciding frame), or — when it is a branch/unrecognized shape — its own line
     * followed by its recursed children. {@code seedSubject} pre-seeds the fold's subject, so a member arm emitted under a
     * protocol frame still reads "Attribute 'x' of 'A' …" rather than losing the owner.
     */
    private static void emitFolded(PyTypeMismatchExplanation node, int depth, List<ProseLine> out, @Nullable CodifiedParam seedSubject) {
        Fold folded = foldChain(node, seedSubject);
        if (folded instanceof Fold.Terminal terminal) {
            out.add(new ProseLine(depth, composeTerminal(terminal)));
            if (terminal.typeArgs != null) {
                emitTypeArgs(terminal.typeArgs, depth + 1, out);
            }
        }
        else if (folded instanceof Fold.Decided decided) {
            out.add(new ProseLine(depth, decided.message));
        }
        else {
            // An unrecognized node: keep its own line, then recurse its reasons one level deeper.
            out.add(new ProseLine(depth, node.getMessage()));
            for (PyTypeMismatchExplanation child : node.getChildren()) {
                emit(child, depth + 1, out);
            }
        }
    }

    /**
     * The child reasons worth expanding under a frame that already stated the top-level mismatch (a union header, a
     * contravariant parameter): those with structure beyond "the value simply isn't that type". A bare nominal leaf
     * ({@code None is not assignable to int}) only restates the frame line, so it is dropped; a member that failed on a
     * protocol attribute, an element type, etc. is kept.
     */
    private static List<PyTypeMismatchExplanation> nonTrivialReasons(PyTypeMismatchExplanation node) {
        List<PyTypeMismatchExplanation> result = new ArrayList<>();
        for (PyTypeMismatchExplanation child : node.getChildren()) {
            if (!isTrivialReason(child)) {
                result.add(child);
            }
        }
        return result;
    }

    /**
     * Whether {@code node} only restates its parent union header: a bare nominal leaf ({@code None is not assignable to int}), or
     * a per-member wrapper whose lone reason is itself such a leaf ({@code str is not assignable to int} over the same).
     * Either adds nothing over the header, so the arm is dropped.
     */
    private static boolean isTrivialReason(PyTypeMismatchExplanation node) {
        return (node.getStep() instanceof PyMismatchStep.Nominal && node.getChildren().isEmpty()) ||
            (isMemberWrapper(node.getStep()) && node.getChildren().size() == 1 && isTrivialReason(node.getChildren().get(0)));
    }

    /**
     * Whether {@code step} is a per-member wrapper under a union or an intersection branch.
     */
    private static boolean isMemberWrapper(@Nullable PyMismatchStep step) {
        return step instanceof PyMismatchStep.UnionMember || step instanceof PyMismatchStep.IntersectionMember;
    }

    /**
     * Emits the structural arms of a union under its header, each named by the member it failed against, so the
     * reader can tell the lines apart ({@code 'A' needs 'a' to be 'int', but 'C' has 'None'} vs the {@code 'B'}/{@code 'str'} arm) —
     * otherwise two "`Attribute a` of `C`" lines that differ only in the expected type are indistinguishable. An arm
     * whose failing member isn't a protocol (no member name to show) folds normally.
     */
    private static void emitUnionArms(PyTypeMismatchExplanation node, int depth, List<ProseLine> out) {
        for (PyTypeMismatchExplanation child : nonTrivialReasons(node)) {
            // A per-member wrapper (an actual-side union names each failing member) folds through to its reason; an
            // expected-side arm is the reason directly. Either way [foldChain] lands on the member's terminal reason.
            PyTypeMismatchExplanation reason = isMemberWrapper(child.getStep())
                ? (child.getChildren().size() == 1 ? child.getChildren().get(0) : child)
                : child;
            Fold fold = foldChain(reason);
            ProblemMessage arm = fold instanceof Fold.Terminal terminal && terminal.member != null ? composeUnionArm(terminal) : null;
            // The reason self-names the member (a missing / wrong-typed attribute): keep one flat arm, drop the wrapper.
            if (arm != null) {
                out.add(new ProseLine(depth, arm));
                if (fold instanceof Fold.Terminal terminal && terminal.typeArgs != null) {
                    emitTypeArgs(terminal.typeArgs, depth + 1, out);
                }
            }
            // It doesn't fold to a one-line arm. If the reason emits its OWN member-naming header (a protocol frame
            // "`B` is incompatible with protocol `P`"), the "`B` is not assignable to `P`" wrapper only restates it, so
            // drop the wrapper and emit the reason directly. Otherwise (e.g. a callable parameter mismatch that names
            // no member) keep the wrapper line to identify the member, with the reason beneath it.
            else if (isMemberWrapper(child.getStep())) {
                if (reason.getStep() instanceof PyMismatchStep.Protocol) {
                    emit(reason, depth, out);
                }
                else {
                    out.add(new ProseLine(depth, child.getMessage()));
                    for (PyTypeMismatchExplanation grandchild : child.getChildren()) {
                        emit(grandchild, depth + 1, out);
                    }
                }
            }
            else {
                emit(child, depth, out);
            }
        }
    }

    /**
     * Drills into a nested type-argument mismatch, one "expected …, got …" line per level (the element of a {@code list},
     * then the element of its {@code set}, …), so the reader sees exactly where deep inside a parameterized type the types
     * diverge rather than a {@code [1][1]} position path. Stops at the deepest recorded type-argument level.
     */
    private static void emitTypeArgs(PyTypeMismatchExplanation node, int depth, List<ProseLine> out) {
        if (!(node.getStep() instanceof PyMismatchStep.TypeArgument step) || step.expected() == null || step.actual() == null) {
            return;
        }
        out.add(new ProseLine(depth, PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseTypeArgDiff(params[0], params[1]), step.expected(), step.actual())));
        PyTypeMismatchExplanation deeper = null;
        for (PyTypeMismatchExplanation child : node.getChildren()) {
            if (child.getStep() instanceof PyMismatchStep.TypeArgument) {
                deeper = child;
                break;
            }
        }
        if (deeper != null) {
            emitTypeArgs(deeper, depth + 1, out);
        }
    }

    /**
     * A member-named union arm line, phrased from the member's requirement so the member is the subject.
     */
    private static @Nullable ProblemMessage composeUnionArm(Fold.Terminal chain) {
        CodifiedParam member = chain.member;
        if (member == null) {
            return null;
        }
        PyMismatchStep step = chain.step;
        if (step instanceof PyMismatchStep.Missing missing) {
            if (missing.isMethod()) {
                return PyInspectionMessages.problemMessage(
                    params -> PyLocalize.inspTypeCheckerProseUnionMethodMissing(params[0], params[1], params[2]),
                    chain.subject, missing.name(), member);
            }
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseUnionMemberMissing(params[0], params[1], params[2]),
                chain.subject, missing.name(), member);
        }
        else if (step instanceof PyMismatchStep.Nominal nominal) {
            if (chain.path.isEmpty()) {
                return null; // no attribute path to attribute the failure to: fall back to the plain fold
            }
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseUnionMemberAttribute(params[0], params[1], params[2], params[3], params[4]),
                member, chain.path.render(), nominal.expected(), chain.subject, nominal.actual());
        }
        return null; // a variance / typed-dict arm: fall back to the plain fold
    }

    /**
     * The outcome of walking a single-child descent chain (see {@link #foldChain}).
     */
    private sealed interface Fold permits Fold.Terminal, Fold.Decided {
        /**
         * The chain reached a terminal {@code step} (nominal, missing, variance) under {@code subject} at access {@code path}. {@code member}
         * is the protocol the chain was tried against, set only when the chain is a union arm (used to name it).
         * {@code typeArgs}, when set, is the type-argument frame nested inside a parameterized attribute — the renderer
         * shows the attribute's types on the terminal line and then drills into {@code typeArgs} with a line per level.
         */
        final class Terminal implements Fold {
            final @Nullable CodifiedParam subject;
            final @Nullable CodifiedParam member;
            final PathBuilder path;
            final PyMismatchStep step;
            final @Nullable PyTypeMismatchExplanation typeArgs;

            Terminal(@Nullable CodifiedParam subject,
                     @Nullable CodifiedParam member,
                     PathBuilder path,
                     PyMismatchStep step,
                     @Nullable PyTypeMismatchExplanation typeArgs) {
                this.subject = subject;
                this.member = member;
                this.path = path;
                this.step = step;
                this.typeArgs = typeArgs;
            }

            Terminal(@Nullable CodifiedParam subject, @Nullable CodifiedParam member, PathBuilder path, PyMismatchStep step) {
                this(subject, member, path, step, null);
            }
        }

        /**
         * A descent frame decided the failure on its own (no nested reason), e.g. an attribute that isn't writable.
         */
        final class Decided implements Fold {
            final ProblemMessage message;

            Decided(ProblemMessage message) {
                this.message = message;
            }
        }
    }

    private static @Nullable Fold foldChain(PyTypeMismatchExplanation node) {
        return foldChain(node, null);
    }

    /**
     * Walks the single-child descent chain from {@code node}, accumulating the subject type and an access path, and
     * returns how it bottomed out — {@link Fold.Terminal} at a terminal reason, {@link Fold.Decided} when a descent frame
     * decided on its own, or {@code null} when {@code node} is a branch (a union, a fan-out) or has an unrecognized shape.
     */
    private static @Nullable Fold foldChain(PyTypeMismatchExplanation node, @Nullable CodifiedParam initialSubject) {
        CodifiedParam subject = initialSubject;
        CodifiedParam member = null;
        CodifiedParam attributeActual = null;
        CodifiedParam attributeExpected = null;
        PathBuilder path = new PathBuilder();
        PyTypeMismatchExplanation cur = node;
        while (true) {
            PyMismatchStep step = cur.getStep();
            if (step == null) {
                return null;
            }
            if (step instanceof PyMismatchStep.Protocol protocol) {
                if (subject == null) {
                    subject = protocol.subject();
                }
                if (member == null) {
                    member = protocol.expected();
                }
            }
            else if (step instanceof PyMismatchStep.Attribute attribute) {
                path.attribute(attribute.name());
                attributeActual = attribute.actual();
                attributeExpected = attribute.expected();
            }
            // A mismatch inside a parameterized attribute type: don't descend into meaningless `[1][1]` type-argument
            // positions — report the attribute's own types on the terminal line, then hand the type-argument subtree to
            // the renderer to drill into with a readable "expected …, got …" line per level. (A bare generic with no
            // enclosing attribute keeps the positional path.)
            else if (step instanceof PyMismatchStep.TypeArgument typeArgument) {
                if (attributeActual != null && attributeExpected != null) {
                    return new Fold.Terminal(subject, member, path, new PyMismatchStep.Nominal(attributeActual, attributeExpected), cur);
                }
                else {
                    path.typeArgument(typeArgument.oneBasedIndex());
                }
            }
            else if (step instanceof PyMismatchStep.Return) {
                path.returnType();
            }
            // Prefer the enclosing attribute's own types over a nested leaf: for `a: list[set[int]]` vs `list[set[None]]`
            // the match records the innermost `None`/`int` directly under the attribute (no type-argument frames), so
            // reporting that leaf reads "a to be int" (wrong — a is a list) and, under an invariant leg, backwards. The
            // attribute's types are the meaningful, correctly-oriented pair.
            else if (step instanceof PyMismatchStep.Nominal) {
                return new Fold.Terminal(subject, member, path,
                                         attributeActual != null && attributeExpected != null
                                             ? new PyMismatchStep.Nominal(attributeActual, attributeExpected)
                                             : step);
            }
            else if (step instanceof PyMismatchStep.Missing ||
                step instanceof PyMismatchStep.Invariant ||
                step instanceof PyMismatchStep.TypedDictKey) {
                return new Fold.Terminal(subject, member, path, step);
            }
            // Frames that carry their own line AND a nested reason (or are transparent groupings): not silently
            // foldable — [emit]/[emitUnionArms] handle them.
            else {
                return null;
            }
            // Only descent steps reach here; continue folding iff there is exactly one child to descend into.
            int size = cur.getChildren().size();
            if (size == 1) {
                cur = cur.getChildren().get(0);
            }
            else if (size == 0) {
                return new Fold.Decided(cur.getMessage()); // a descent that decided on its own (e.g. an attribute not writable)
            }
            else {
                return null;                               // a descent that fans out: fall back to the header + recursed children
            }
        }
    }

    private static ProblemMessage composeTerminal(Fold.Terminal chain) {
        PyMismatchStep step = chain.step;
        if (step instanceof PyMismatchStep.Nominal nominal) {
            return nominal(chain.subject, chain.path, nominal.actual(), nominal.expected());
        }
        else if (step instanceof PyMismatchStep.Missing missing) {
            return missing(chain.subject, missing.name(), missing.isMethod());
        }
        else if (step instanceof PyMismatchStep.Invariant invariant) {
            return invariant(invariant);
        }
        else if (step instanceof PyMismatchStep.TypedDictKey typedDictKey) {
            return typedDictKey(typedDictKey);
        }
        throw new IllegalStateException("non-terminal step " + chain.step + " reached composeTerminal");
    }

    /**
     * The invariance line, with an optional "use `Sequence` if you only read from it" hint for list/set/dict.
     */
    private static ProblemMessage invariant(PyMismatchStep.Invariant step) {
        if (step.readOnlyAlternative() != null) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseInvariantSuggest(params[0], params[1], params[2], params[3], params[4]),
                step.owner(), step.typeVar(), step.actual(), step.expected(), step.readOnlyAlternative());
        }
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseInvariant(params[0], params[1], params[2], params[3]),
            step.owner(), step.typeVar(), step.actual(), step.expected());
    }

    /**
     * The TypedDict key line: a missing key, a wrong value type, a read-only source key, or a required mismatch.
     */
    private static ProblemMessage typedDictKey(PyMismatchStep.TypedDictKey step) {
        return switch (step.problem()) {
            case MISSING -> PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseTypedDictMissing(params[0], params[1]), step.owner(), step.key());
            case VALUE_TYPE -> step.actual() != null && step.expected() != null
                ? PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseTypedDictType(params[0], params[1], params[2], params[3]),
                step.owner(), step.key(), step.actual(), step.expected())
                : PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseTypedDictTypePlain(params[0], params[1]), step.owner(), step.key());
            case READONLY -> PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseTypedDictReadonly(params[0], params[1]), step.owner(), step.key());
            case REQUIRED -> PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseTypedDictRequired(params[0], params[1]), step.owner(), step.key());
        };
    }

    /**
     * The "Parameter … is called with X, but only accepts Y" line; identified by name, else 1-based position.
     */
    private static ProblemMessage contravariant(PyMismatchStep.ContravariantParameter step) {
        if (step.name() != null) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseContravariant(params[0], params[1], params[2]),
                step.name(), step.required(), step.provided());
        }
        else if (step.oneBasedIndex() != null) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseContravariantIndexed(params[0], params[1], params[2]),
                step.oneBasedIndex(), step.required(), step.provided());
        }
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseContravariantUnnamed(params[0], params[1]),
            step.required(), step.provided());
    }

    private static ProblemMessage nominal(@Nullable CodifiedParam subject,
                                          PathBuilder path,
                                          CodifiedParam actual,
                                          CodifiedParam expected) {
        if (path.isReturnOnly()) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseReturnNominal(params[0], params[1]), actual, expected);
        }
        else if (subject != null && !path.isEmpty()) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseMemberNominal(params[0], params[1], params[2], params[3]),
                subject, path.render(), actual, expected);
        }
        else if (!path.isEmpty()) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProsePathNominal(params[0], params[1], params[2]),
                path.render(), actual, expected);
        }
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerTypeNotAssignable(params[0], params[1]), actual, expected);
    }

    private static ProblemMessage missing(@Nullable CodifiedParam subject, String name, boolean isMethod) {
        if (subject != null) {
            return isMethod
                ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerProseMethodMissing(params[0], params[1]), subject, name)
                : PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerProseMemberMissing(params[0], params[1]), subject, name);
        }
        return isMethod
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownMethodMissing(params[0]), name)
            : PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownMemberMissing(params[0]), name);
    }

    // ---- call-site requirement/actual lines -------------------------------------------------------------------

    /**
     * Joins a {@code requirement} with its {@code actual}-value clause into one sentence ("…, but `arg` is `T`"), or keeps only the
     * requirement when the argument is a literal ({@link CallSite#getActualIsLiteral()}) — whose value the argument expression
     * already shows, so "`"a"` is `Literal["a"]`" would only restate it.
     */
    private static ProblemMessage oneLine(CallSite callSite, ProblemMessage requirement, ProblemMessage actual) {
        if (callSite.actualIsLiteral) {
            return requirement;
        }
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgLineJoin(params[0], params[1]), asParam(requirement), asParam(actual));
    }

    /**
     * Re-wraps an already-rendered {@link ProblemMessage} as a {@link CodifiedParam} (plain description + rich tooltip), so it can
     * be embedded verbatim — with its own {@code <code>} spans intact — inside another template.
     */
    private static CodifiedParam asParam(ProblemMessage message) {
        return new CodifiedParam(message.description(), PyInspectionMessages.tooltipFragment(message));
    }

    /**
     * The "parameter `name` of type `T`" (or "an argument of type `T`") phrase, as one param that already carries
     * its own {@code <code>} spans, so the requirement templates embed it without adding another.
     */
    private static CodifiedParam parameterPhrase(CallSite callSite) {
        return asParam(
            callSite.parameterName != null
                ? PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseArgParamNamed(params[0], params[1]), callSite.parameterName, callSite.parameterType)
                : PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseArgParamUnnamed(params[0]), callSite.parameterType));
    }

    private static ProblemMessage requirementMissing(CallSite callSite, String name) {
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgReqMissing(params[0], params[1], params[2]),
            callSite.callee, parameterPhrase(callSite), name);
    }

    private static ProblemMessage actualMissing(CallSite callSite, String name) {
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgActMissing(params[0], params[1], params[2]),
            callSite.argument, callSite.actualType, name);
    }

    private static ProblemMessage requirementAttribute(CallSite callSite, String path, CodifiedParam expected) {
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgReqAttribute(params[0], params[1], params[2], params[3]),
            callSite.callee, parameterPhrase(callSite), path, expected);
    }

    private static ProblemMessage actualAttribute(CallSite callSite, String path, CodifiedParam actual) {
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgActAttribute(params[0], params[1], params[2], params[3]),
            callSite.argument, callSite.actualType, path, actual);
    }

    private static ProblemMessage requirementNominal(CallSite callSite) {
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgReqNominal(params[0], params[1]), callSite.callee, parameterPhrase(callSite));
    }

    private static ProblemMessage actualNominal(CallSite callSite) {
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgActNominal(params[0], params[1]), callSite.argument, callSite.actualType);
    }

    private static ProblemMessage unionRequirement(CallSite callSite, CodifiedParam union) {
        if (callSite.parameterName != null) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerProseArgReqUnionNamed(params[0], params[1], params[2]),
                callSite.callee, callSite.parameterName, union);
        }
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgReqUnionUnnamed(params[0], params[1]), callSite.callee, union);
    }

    private static ProblemMessage actualUnion(CallSite callSite) {
        return PyInspectionMessages.problemMessage(
            params -> PyLocalize.inspTypeCheckerProseArgActUnion(params[0], params[1]), callSite.argument, callSite.actualType);
    }

    /**
     * Accumulates an access path from descent steps: attributes join with dots ({@code inner.value}), type arguments
     * append a bracketed position ({@code items[1]}). A lone return-type descent is tracked separately so it can read
     * "The return type is …" instead of being forced into the path grammar.
     */
    private static final class PathBuilder {
        private final StringBuilder sb = new StringBuilder();
        private int segments = 0;
        private boolean isReturnOnly = false;

        boolean isReturnOnly() {
            return isReturnOnly;
        }

        boolean isEmpty() {
            return sb.isEmpty();
        }

        void attribute(String name) {
            if (!sb.isEmpty()) {
                sb.append('.');
            }
            sb.append(name);
            segments++;
            isReturnOnly = false;
        }

        void typeArgument(int oneBasedIndex) {
            sb.append('[').append(oneBasedIndex).append(']');
            segments++;
            isReturnOnly = false;
        }

        void returnType() {
            isReturnOnly = segments == 0;
            segments++;
        }

        String render() {
            return sb.toString();
        }
    }
}
