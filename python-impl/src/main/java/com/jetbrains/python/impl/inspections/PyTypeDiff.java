// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyTypeDiffGrid.Cell;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.types.*;
import consulo.application.util.HtmlChunk;
import consulo.application.util.registry.Registry;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StackOverflowPreventedException;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Renders a side-by-side structural "type diff" for the editor-hover tooltip of a type mismatch. The two types
 * are laid out as two aligned, code-styled rows (via {@link PyTypeDiffGrid}) whose matching sub-parts line up vertically
 * and whose incompatible sub-parts are highlighted. The first (top) row is the expected type, the second is the
 * actual (provided) one.
 * <p>
 * The diff recurses into structure that both types share, aligning the corresponding pieces:
 * <ul>
 * <li><b>callables</b> — parameter names and types in separate aligned columns, defaults as {@code = ...}, {@code *args}/{@code /}/{@code *}
 *   handled (parameters are contravariant; the return type recurses and is covariant);</li>
 * <li><b>tuples</b> and <b>generic classes</b> ({@code list}/{@code Sequence}/…) of the same arity — each type argument is aligned
 *   and recursed into, e.g.
 *   <pre>
 *   tuple[bool, list    [str],    int]
 *   tuple[int,  Sequence[object], str]
 *   </pre></li>
 * <li>anything else is a leaf: the two type names are shown as a single aligned pair.</li>
 * </ul>
 * <p>
 * A leaf-vs-leaf mismatch (a plain scalar like {@code int} vs {@code str}) has no internal structure to align, so
 * {@link #diffTooltip} returns {@code null} there and the caller keeps its plain "Expected …, got …" tooltip.
 */
public final class PyTypeDiff {
    private PyTypeDiff() {
    }

    /**
     * How deep the structural decomposition may nest before bailing out, to stop recursive types looping forever.
     * Well beyond any realistically hand-written type, so it never truncates a genuine one.
     */
    private static final int MAX_NESTING_DEPTH = 20;

    /**
     * One aligned column: the cell shown on the actual (top) row and the cell shown on the expected (bottom) row.
     */
    private record Col(Cell actual, Cell expected) {
    }

    public static @Nullable String diffTooltip(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context) {
        return diffTooltip(expected, actual, context, false);
    }

    /**
     * Builds the diff tooltip for an {@code expected}/{@code actual} pair of types with the default headline, or returns
     * {@code null} when there is no shared structure worth aligning (a plain scalar mismatch, or a bare {@code ...} callable).
     */
    public static @Nullable String diffTooltip(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context, boolean exact) {
        return diffTooltip(expected, actual, defaultHeadline(expected, actual), context, exact);
    }

    /**
     * The headline for a plain expected/actual mismatch. When either type is a named callable whose name the
     * aligned rows don't show — a callable {@code Protocol} (or other class with a {@code __call__}), or a function — the name
     * is woven into the sentence so the reader knows which type the structural signature belongs to (e.g.
     * "`fn` does not match the expected `Comparator`:"). Otherwise it is the generic provided-vs-expected wording.
     */
    private static HtmlChunk defaultHeadline(@Nullable PyType expected, @Nullable PyType actual) {
        String expectedName = namedCallableType(expected);
        String actualName = namedCallableType(actual);
        String message;
        if (actualName != null && expectedName != null) {
            message = PyLocalize.inspTypeCheckerTypeMismatchHeaderNamedBoth(actualName, expectedName).get();
        }
        else if (expectedName != null) {
            message = PyLocalize.inspTypeCheckerTypeMismatchHeaderNamedExpected(expectedName).get();
        }
        else if (actualName != null) {
            message = PyLocalize.inspTypeCheckerTypeMismatchHeaderNamedProvided(actualName).get();
        }
        else {
            message = PyLocalize.inspTypeCheckerTypeMismatchHeader().get();
        }
        // The header templates mark the type names with backticks; render those as `<code>` spans, not literal backticks.
        return HtmlChunk.raw(PyInspectionMessages.codeSpansToHtmlFragment(message));
    }

    /**
     * A user-facing name for a callable type whose structural rendering hides it: a callable {@code Protocol} (or any
     * other class with a {@code __call__}), shown by its class name, or a function/lambda, shown by its name. A bare
     * {@code Callable[...]} has no such name, and a non-callable type's own name (e.g. {@code list}) already appears in the
     * aligned rows — so both return {@code null} and the type isn't named in the headline.
     */
    private static @Nullable String namedCallableType(@Nullable PyType type) {
        if (!(type instanceof PyCallableType callableType) || !callableType.isCallable()) {
            return null;
        }
        if (type instanceof PyClassType classType) {
            return classType.getPyClass().getName();
        }
        if (type instanceof PyFunctionType functionType) {
            return functionType.getCallable().getName();
        }
        return null;
    }

    public static @Nullable String diffTooltip(@Nullable PyType expected,
                                               @Nullable PyType actual,
                                               HtmlChunk headline,
                                               TypeEvalContext context) {
        return diffTooltip(expected, actual, headline, context, false);
    }

    /**
     * Builds the diff tooltip for an {@code expected}/{@code actual} pair of types, showing {@code headline} (an inspection's own
     * message) above the diff. Returns {@code null} when there is no shared structure worth aligning.
     */
    public static @Nullable String diffTooltip(@Nullable PyType expected,
                                               @Nullable PyType actual,
                                               HtmlChunk headline,
                                               TypeEvalContext context,
                                               boolean exact) {
        if (!diffTooltipsEnabled()) {
            return null;
        }
        // `exact` (assert_type) requires the two types to be identical, so compare invariantly — any structural
        // difference shows, even one where a side would merely be assignable to the other.
        Variance variance = exact ? Variance.INVARIANT : Variance.COVARIANT;
        List<Col> columns = recursionSafe(() -> structuredColumns(expected, actual, context, variance, 0));
        if (columns == null) {
            return null;
        }
        return render(columns, headline);
    }

    public static String paramsDiffTooltip(List<PyCallableParameter> expectedParameters,
                                           List<PyCallableParameter> actualParameters,
                                           HtmlChunk headline,
                                           TypeEvalContext context) {
        return paramsDiffTooltip(expectedParameters, actualParameters, headline, context, null, null);
    }

    public static String paramsDiffTooltip(List<PyCallableParameter> expectedParameters,
                                           List<PyCallableParameter> actualParameters,
                                           HtmlChunk headline,
                                           TypeEvalContext context,
                                           @Nullable PyTypeParameterList expectedTypeParameters) {
        return paramsDiffTooltip(expectedParameters, actualParameters, headline, context, expectedTypeParameters, null);
    }

    /**
     * Builds the diff tooltip comparing two parameter lists only (no return type), for overload-vs-implementation
     * and method-override signature mismatches. The given {@code headline} is shown above the diff.
     */
    public static String paramsDiffTooltip(List<PyCallableParameter> expectedParameters,
                                           List<PyCallableParameter> actualParameters,
                                           HtmlChunk headline,
                                           TypeEvalContext context,
                                           @Nullable PyTypeParameterList expectedTypeParameters,
                                           @Nullable PyTypeParameterList actualTypeParameters) {
        if (!diffTooltipsEnabled()) {
            return plainTooltip(headline);
        }
        // The `self`/`cls` receiver is kept in the rendered signature but its type is shown only when EXPLICITLY
        // annotated, and two implicit receivers are never flagged (their `Self@A` vs `Self@B` difference is legitimate) —
        // see [layout] (display) and [isTypeMismatch] (comparison).
        List<Col> paramColumns = recursionSafe(() -> parameterListColumns(expectedParameters, actualParameters, context, Variance.CONTRAVARIANT, 0));
        if (paramColumns == null) {
            return plainTooltip(headline);
        }
        // A leading `[T: bound]` type-parameter group (when the methods declare type parameters) so a differing bound —
        // base `def f[T: str]` overridden by `def f[T: int]` — is highlighted; otherwise the two rows look identical.
        List<Col> columns = new ArrayList<>(typeParameterColumns(expectedTypeParameters, actualTypeParameters, context));
        columns.addAll(paramColumns);
        return render(columns, headline);
    }

    /**
     * A leading {@code [T: bound, …]} type-parameter-list group for the two signatures, aligned position by position so a
     * differing bound (base {@code def f[T: str]} vs override {@code def f[T: int]}) is highlighted. Empty when neither declares any.
     */
    private static List<Col> typeParameterColumns(@Nullable PyTypeParameterList expected,
                                                  @Nullable PyTypeParameterList actual,
                                                  TypeEvalContext context) {
        List<PyTypeParameter> expectedParams = expected != null ? expected.getTypeParameters() : List.of();
        List<PyTypeParameter> actualParams = actual != null ? actual.getTypeParameters() : List.of();
        if (expectedParams.isEmpty() && actualParams.isEmpty()) {
            return List.of();
        }
        List<Col> columns = new ArrayList<>();
        columns.add(delimColumn("["));
        for (int i = 0; i < Math.max(expectedParams.size(), actualParams.size()); i++) {
            if (i > 0) {
                columns.add(delimColumn(", "));
            }
            columns.addAll(typeParameterSlot(getOrNull(expectedParams, i), getOrNull(actualParams, i), context));
        }
        columns.add(delimColumn("]"));
        return columns;
    }

    /**
     * One aligned type parameter: its name (highlighted only when the two sides name it differently) and, when either
     * side bounds it, a {@code : } and the recursed bound diff (so {@code T: str} vs {@code T: int} highlights just {@code str}/{@code int}).
     */
    private static List<Col> typeParameterSlot(@Nullable PyTypeParameter expected, @Nullable PyTypeParameter actual, TypeEvalContext context) {
        String expectedName = expected != null && expected.getName() != null ? expected.getName() : "";
        String actualName = actual != null && actual.getName() != null ? actual.getName() : "";
        boolean nameMismatch = !expectedName.equals(actualName);
        Col nameCol = new Col(PyTypeDiffGrid.value(actualName, nameMismatch), PyTypeDiffGrid.value(expectedName, nameMismatch));
        PyType expectedBound = typeParameterBound(expected, context);
        PyType actualBound = typeParameterBound(actual, context);
        if (expectedBound == null && actualBound == null) {
            return List.of(nameCol);
        }
        List<Col> result = new ArrayList<>();
        result.add(nameCol);
        result.add(delimColumn(": "));
        result.addAll(columns(expectedBound, actualBound, context, Variance.INVARIANT, 0));
        return result;
    }

    private static @Nullable PyType typeParameterBound(@Nullable PyTypeParameter typeParameter, TypeEvalContext context) {
        PyExpression boundExpression = typeParameter != null ? typeParameter.getBoundExpression() : null;
        if (boundExpression == null) {
            return null;
        }
        // TODO consulo: Ref.deref missing
        Ref<PyType> ref = PyTypingTypeProvider.getType(boundExpression, context);
        return ref != null ? ref.get() : null;
    }

    /**
     * Whether the structural side-by-side type-diff tooltips are enabled (registry-gated, off by default). When off,
     * inspections fall back to their plain message tooltip; the Problems-view description is unaffected either way.
     */
    public static boolean diffTooltipsEnabled() {
        return Registry.is("python.type.checker.diff.tooltip", true);
    }

    /**
     * Building the diff evaluates parameter/return types, which can hit the type system's recursion guard on
     * self-referential built-ins like {@code object}/{@code type} (it returns a null type in production, throws in tests). The
     * diff is a best-effort enhancement, so a prevented recursion just drops it — the caller keeps the plain message.
     */
    private static @Nullable List<Col> recursionSafe(Supplier<@Nullable List<Col>> build) {
        try {
            return build.get();
        }
        catch (StackOverflowPreventedException e) {
            return null;
        }
    }

    /**
     * A minimal tooltip showing just {@code headline}, with no diff grid.
     */
    private static String plainTooltip(HtmlChunk headline) {
        return headline.wrapWith("html").toString();
    }

    private static String render(List<Col> columns, HtmlChunk headline) {
        // The expected signature is shown on top and the provided (actual) one below, each clearly labeled. Like an
        // editor diff, the incompatible parts of the expected type are highlighted in green and those of the provided
        // value in red.
        List<Cell> expectedCells = new ArrayList<>();
        List<Cell> actualCells = new ArrayList<>();
        for (Col col : columns) {
            expectedCells.add(col.expected());
            actualCells.add(col.actual());
        }
        List<PyTypeDiffGrid.Row> rows = List.of(
            new PyTypeDiffGrid.Row(PyLocalize.inspTypeCheckerDiffExpectedLabel().get(), expectedCells, PyTypeDiffGrid.Side.EXPECTED),
            new PyTypeDiffGrid.Row(PyLocalize.inspTypeCheckerDiffActualLabel().get(), actualCells, PyTypeDiffGrid.Side.PROVIDED)
        );
        return PyTypeDiffGrid.tooltip(headline, rows);
    }

    // ---- Structural decomposition -------------------------------------------------------------------------------

    /**
     * The variance of a position in the type tree, controlling which direction of assignability must hold there
     * (and therefore which side is the union "source" and how the related-class check is oriented):
     * <ul>
     * <li><b>covariant</b> — the actual value must be assignable to the expected one (return types, tuple elements,
     *   most type arguments);</li>
     * <li><b>contravariant</b> — the expected value must be accepted by the actual one (callable parameter types);</li>
     * <li><b>invariant</b> — both must hold (e.g. the element of a {@code list}, which is invariant). An invariant position
     *   propagates through everything nested in it, so e.g. a {@code Callable} inside a {@code list} has invariant parameters.</li>
     * </ul>
     */
    private enum Variance {
        COVARIANT, CONTRAVARIANT, INVARIANT;

        /**
         * The variance of a position nested at {@code inner} variance inside a position of this variance (the usual
         * variance "multiplication": invariant is absorbing, otherwise like signs multiply).
         */
        Variance then(Variance inner) {
            if (this == INVARIANT || inner == INVARIANT) {
                return INVARIANT;
            }
            if (this == inner) {
                return COVARIANT;
            }
            return CONTRAVARIANT;
        }
    }

    /**
     * Whether the {@code actual}/{@code expected} pair is incompatible at a position of the given {@code variance}.
     */
    private static boolean typesMismatch(@Nullable PyType actual, @Nullable PyType expected, Variance variance, TypeEvalContext context) {
        return switch (variance) {
            case COVARIANT -> !PyTypeChecker.match(expected, actual, context);
            case CONTRAVARIANT -> !PyTypeChecker.match(actual, expected, context);
            case INVARIANT -> !PyTypeChecker.match(expected, actual, context) || !PyTypeChecker.match(actual, expected, context);
        };
    }

    /**
     * The declared (or inferred) variance of the {@code index}th type argument of {@code generic}, mirroring
     * {@code PyTypeChecker.findTypeParameterVariance}: tuples are covariant, unknown parameters default to covariant.
     */
    private static Variance declaredVariance(PyClassType generic, int index, TypeEvalContext context) {
        if (generic instanceof PyTupleType) {
            return Variance.COVARIANT;
        }
        PyClassType definition = PyTypeChecker.findGenericDefinitionType(generic.getPyClass(), context);
        PyType typeArgument = definition != null ? getOrNull(definition.getTypeArguments(), index) : null;
        if (!(typeArgument instanceof PyTypeParameterType typeParameter)) {
            return Variance.COVARIANT;
        }
        if (!(typeParameter instanceof PyTypeVarType)) {
            return Variance.COVARIANT;
        }
        PyVariance variance = PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeParameter, context);
        if (variance == PyVariance.CONTRAVARIANT) {
            return Variance.CONTRAVARIANT;
        }
        if (variance == PyVariance.INVARIANT) {
            return Variance.INVARIANT;
        }
        return Variance.COVARIANT;
    }

    /**
     * The columns for one aligned pair: shared structure recursed into, or a single leaf pair.
     */
    private static List<Col> columns(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context, Variance variance, int depth) {
        List<Col> structured = structuredColumns(expected, actual, context, variance, depth);
        return structured != null ? structured : leafColumns(expected, actual, context, variance);
    }

    /**
     * Like {@link #columns} but returns {@code null} when neither side decomposes into alignable structure.
     */
    private static @Nullable List<Col> structuredColumns(@Nullable PyType expected,
                                                         @Nullable PyType actual,
                                                         TypeEvalContext context,
                                                         Variance variance,
                                                         int depth) {
        // Recursive types (e.g. `class Node(list[Node])`) would otherwise decompose forever — and we descend the
        // structure directly rather than through PyTypeChecker, so there is no shared cycle guard. Past the limit,
        // stop decomposing and let the caller fall back to a flat leaf/cell.
        if (depth > MAX_NESTING_DEPTH) {
            return null;
        }
        List<Col> composite = compositeColumns(expected, actual, context, variance, depth);
        if (composite != null) {
            return composite;
        }
        if (expected instanceof PyCallableType expectedCallable && actual instanceof PyCallableType actualCallable &&
            expectedCallable.isCallable() && actualCallable.isCallable()) {
            List<Col> callable = callableColumns(expectedCallable, actualCallable, context, variance, depth);
            if (callable != null) {
                return callable;
            }
        }
        List<Col> tuple = tupleColumns(expected, actual, context, variance, depth);
        if (tuple != null) {
            return tuple;
        }
        List<Col> generic = genericColumns(expected, actual, context, variance, depth);
        if (generic != null) {
            return generic;
        }
        return null;
    }

    /**
     * A type name for the diff, with literals shown bare ({@code 1}, not {@code Literal[1]}) — the {@code Literal[…]} wrapper is
     * redundant noise in a structural diff, where the value alone is clearer.
     */
    private static String bareTypeName(@Nullable PyType type, TypeEvalContext context) {
        return PythonDocumentationProvider.getTypeName(type, context, PyTypeRendererFeature.BARE_LITERAL);
    }

    private static List<Col> leafColumns(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context, Variance variance) {
        String actualName = bareTypeName(actual, context);
        String expectedName = bareTypeName(expected, context);
        boolean mismatch = typesMismatch(actual, expected, variance, context);
        // Either side may itself be a union: highlight only the members the other side doesn't match, not the whole
        // union — so `int | str` vs `int | float` greys the shared `int` and colours only `str`/`float`. A "source" side
        // (whose values must be accepted) reds each member the other can't take; a "target" side greens each alternative
        // the source doesn't provide. Covariant → actual is the source, expected the target; contravariant flips them;
        // invariant treats each side as a source (every member must round-trip).
        Collection<@Nullable PyType> actualMembers = unionMembers(actual);
        Collection<@Nullable PyType> expectedMembers = unionMembers(expected);
        return switch (variance) {
            case COVARIANT -> List.of(new Col(
                unionAwareCell(actual, actualName, mismatch, context, false, "", a -> !PyTypeChecker.match(expected, a, context)),
                unionAwareCell(expected, expectedName, mismatch, context, false, "",
                    e -> actualMembers.stream().noneMatch(it -> PyTypeChecker.match(e, it, context)))
            ));
            case CONTRAVARIANT -> List.of(new Col(
                unionAwareCell(actual, actualName, mismatch, context, false, "",
                    a -> expectedMembers.stream().noneMatch(it -> PyTypeChecker.match(a, it, context))),
                unionAwareCell(expected, expectedName, mismatch, context, false, "", e -> !PyTypeChecker.match(actual, e, context))
            ));
            case INVARIANT -> List.of(new Col(
                unionAwareCell(actual, actualName, mismatch, context, false, "", a -> !PyTypeChecker.match(expected, a, context)),
                unionAwareCell(expected, expectedName, mismatch, context, false, "", e -> !PyTypeChecker.match(actual, e, context))
            ));
        };
    }

    /**
     * Aligns two types when at least one is a union or intersection, so a top-level composite-type mismatch is shown
     * member by member — the whole-type structural dispatch can't decompose a composite, so without this the diff
     * would fall back to a plain message. Returns null when neither side is a union or intersection.
     */
    private static @Nullable List<Col> compositeColumns(@Nullable PyType expected,
                                                        @Nullable PyType actual,
                                                        TypeEvalContext context,
                                                        Variance variance,
                                                        int depth) {
        boolean isUnion = expected instanceof PyUnionType || actual instanceof PyUnionType;
        boolean isIntersection = expected instanceof PyIntersectionType || actual instanceof PyIntersectionType;
        if (!isUnion && !isIntersection) {
            return null;
        }
        // A top-level composite is aligned member-by-member, so the `|`/`&` separators and the matching members line up
        // across the two rows (the grid pads each member into its own column; a member with no counterpart shows as a
        // gap). A nested composite (a union inside a parameter or type argument) stays a single per-member cell so it
        // fits inside its column.
        if (depth == 0) {
            return alignedCompositeColumns(expected, actual, context, variance, isUnion, depth);
        }
        return isUnion ? leafColumns(expected, actual, context, variance) : intersectionColumns(expected, actual, context);
    }

    /**
     * One aligned member pair of two composite types: the member on the actual (top) row and the one on the expected
     * (bottom) row; either is null when that side has no equivalent member there (rendered as a gap).
     */
    private record CompositeSlot(@Nullable PyType actual, @Nullable PyType expected) {
    }

    /**
     * Aligns the members of two composite types (both unions, or both intersections, or one of them a single type)
     * into grid columns: members that are equivalent — or that merely share structure, like {@code list[int]} and
     * {@code list[str]} — are paired so they line up in one column (recursing so only their differing inner parts show),
     * and the rest are left one-sided against a gap. A one-sided member is highlighted only when it is the
     * incompatible one (see {@link #oneSidedMemberBad}); a gap is never highlighted.
     */
    private static @Nullable List<Col> alignedCompositeColumns(@Nullable PyType expected,
                                                               @Nullable PyType actual,
                                                               TypeEvalContext context,
                                                               Variance variance,
                                                               boolean isUnion,
                                                               int depth) {
        String separator = isUnion ? " | " : " & ";
        List<@Nullable PyType> expectedMembers = new ArrayList<>(isUnion ? unionMembers(expected) : intersectionMembers(expected));
        List<@Nullable PyType> actualMembers = new ArrayList<>(isUnion ? unionMembers(actual) : intersectionMembers(actual));
        List<CompositeSlot> slots = alignCompositeMembers(expectedMembers, actualMembers, context, depth);
        // Two unions with no member in common are entirely disjoint: the "diff" would be only gaps, less useful than the
        // plain "expected …, got …" message, so don't show it (intersections still render — their members are the point).
        if (isUnion && slots.stream().noneMatch(it -> it.expected() != null && it.actual() != null)) {
            return null;
        }
        List<Col> columns = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            CompositeSlot slot = slots.get(i);
            if (i > 0) {
                columns.add(delimColumn(separator));
            }
            if (slot.expected() != null && slot.actual() != null) {
                // Equivalent or structurally related: recurse so the pair lines up in one column and only the differing
                // inner parts (e.g. the `int` vs `str` of `list[int]`/`list[str]`) are highlighted.
                columns.addAll(columns(slot.expected(), slot.actual(), context, variance, depth + 1));
            }
            else {
                // A member on only one side: shown alone against a (never-highlighted) gap, and itself highlighted only
                // when it is the incompatible one.
                PyType member = slot.expected() != null ? slot.expected() : slot.actual();
                boolean onExpected = slot.expected() != null;
                boolean bad = oneSidedMemberBad(member, onExpected, isUnion, expected, actual, variance, context);
                Cell gap = PyTypeDiffGrid.value("", false);
                // A non-highlighted STRUCTURED member (a callable/tuple/generic) would otherwise render as one flat,
                // un-syntax-coloured string (its type name alone carries no link); recurse it against itself so each part
                // gets the platform's colour + links, with the absent opposite side shown as a gap. A highlighted (bad)
                // member, and a plain leaf, keep the single [typeValue] cell (a leaf's own class name is already coloured).
                List<Col> decomposed = bad ? null : structuredColumns(member, member, context, variance, depth + 1);
                if (decomposed != null) {
                    for (Col col : decomposed) {
                        Cell sideCell = onExpected ? col.expected() : col.actual();
                        columns.add(onExpected ? new Col(gap, sideCell) : new Col(sideCell, gap));
                    }
                }
                else {
                    Cell cell = PyTypeDiffGrid.typeValue(member, bareTypeName(member, context), bad);
                    columns.add(onExpected ? new Col(gap, cell) : new Col(cell, gap));
                }
            }
        }
        return columns;
    }

    /**
     * Pairs each expected member with an actual member that is equivalent or, failing that, shares alignable
     * structure (so {@code list[int]} pairs with {@code list[str]}), preserving expected order; unmatched expected members
     * follow as one-sided slots, then any leftover actual members.
     */
    private static List<CompositeSlot> alignCompositeMembers(List<@Nullable PyType> expected,
                                                             List<@Nullable PyType> actual,
                                                             TypeEvalContext context,
                                                             int depth) {
        boolean[] usedActual = new boolean[actual.size()];
        int[] pairing = new int[expected.size()];
        Arrays.fill(pairing, -1);
        BiPredicate<@Nullable PyType, @Nullable PyType> equivalent = (e, a) -> equivalentTypes(e, a, context);
        BiPredicate<@Nullable PyType, @Nullable PyType> sameShape =
            (e, a) -> structuredColumns(e, a, context, Variance.COVARIANT, depth + 1) != null;
        // identical members first,
        pairBy(expected, actual, usedActual, pairing, equivalent);
        // then partial (same-shape) matches
        pairBy(expected, actual, usedActual, pairing, sameShape);
        List<CompositeSlot> slots = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            slots.add(new CompositeSlot(pairing[i] >= 0 ? actual.get(pairing[i]) : null, expected.get(i)));
        }
        for (int j = 0; j < actual.size(); j++) {
            if (!usedActual[j]) {
                slots.add(new CompositeSlot(actual.get(j), null));
            }
        }
        return slots;
    }

    private static void pairBy(List<@Nullable PyType> expected,
                               List<@Nullable PyType> actual,
                               boolean[] usedActual,
                               int[] pairing,
                               BiPredicate<@Nullable PyType, @Nullable PyType> matches) {
        for (int i = 0; i < expected.size(); i++) {
            if (pairing[i] >= 0) {
                continue;
            }
            for (int j = 0; j < actual.size(); j++) {
                if (!usedActual[j] && matches.test(expected.get(i), actual.get(j))) {
                    pairing[i] = j;
                    usedActual[j] = true;
                    break;
                }
            }
        }
    }

    private static boolean equivalentTypes(@Nullable PyType a, @Nullable PyType b, TypeEvalContext context) {
        return PyTypeChecker.match(a, b, context) && PyTypeChecker.match(b, a, context);
    }

    /**
     * Whether a one-sided composite member is the incompatible one. A union under covariance flags only a provided
     * member the expected union can't accept — a provided value may legitimately omit some of the expected
     * alternatives, so an expected-only member stays plain; contravariance is the mirror; invariance flags either
     * side. Every intersection member is required, so any one-sided intersection member differs.
     */
    private static boolean oneSidedMemberBad(@Nullable PyType member,
                                             boolean onExpected,
                                             boolean isUnion,
                                             @Nullable PyType expected,
                                             @Nullable PyType actual,
                                             Variance variance,
                                             TypeEvalContext context) {
        return !isUnion ||
            (onExpected
                ? variance != Variance.COVARIANT && !PyTypeChecker.match(actual, member, context)
                : variance != Variance.CONTRAVARIANT && !PyTypeChecker.match(expected, member, context));
    }

    /**
     * Aligns two intersection types member by member. An intersection is a conjunction — the value is <i>all</i> of its
     * members at once — so unlike a union there is no source/target direction: a member is highlighted simply when the
     * other side has no equivalent member, the way a set diff marks the elements unique to each side.
     */
    private static List<Col> intersectionColumns(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context) {
        Collection<@Nullable PyType> expectedMembers = intersectionMembers(expected);
        Collection<@Nullable PyType> actualMembers = intersectionMembers(actual);
        return List.of(new Col(
            compositeCell(actualMembers, context, it -> absentFrom(expectedMembers, it, context)),
            compositeCell(expectedMembers, context, it -> absentFrom(actualMembers, it, context))
        ));
    }

    private static boolean absentFrom(Collection<@Nullable PyType> others, @Nullable PyType m, TypeEvalContext context) {
        return others.stream().noneMatch(it -> PyTypeChecker.match(it, m, context) && PyTypeChecker.match(m, it, context));
    }

    /**
     * A union's members, or the type itself as a single-member list, for member-by-member comparison.
     */
    private static Collection<@Nullable PyType> unionMembers(@Nullable PyType type) {
        if (type instanceof PyUnionType unionType) {
            return unionType.getMembers();
        }
        return Collections.singletonList(type);
    }

    private static Collection<@Nullable PyType> intersectionMembers(@Nullable PyType type) {
        if (type instanceof PyIntersectionType intersectionType) {
            return intersectionType.getMembers();
        }
        return Collections.singletonList(type);
    }

    /**
     * A cell rendering {@code members} joined by {@code  & } (an intersection), each highlighted when {@code isBad}.
     */
    private static Cell compositeCell(Collection<@Nullable PyType> members, TypeEvalContext context, Predicate<@Nullable PyType> isBad) {
        List<PyTypeDiffGrid.Segment> segments = new ArrayList<>();
        int i = 0;
        for (PyType m : members) {
            if (i > 0) {
                segments.add(PyTypeDiffGrid.segmentDelim(" & "));
            }
            String name = bareTypeName(m, context);
            segments.add(PyTypeDiffGrid.typeSegment(m, name, isBad.test(m)));
            i++;
        }
        return PyTypeDiffGrid.segmented(segments);
    }

    private static Col delimColumn(String text) {
        return new Col(PyTypeDiffGrid.delim(text), PyTypeDiffGrid.delim(text));
    }

    /**
     * A value cell for {@code type} (pre-rendered as {@code typeName}). When the type is a union and the column is a
     * {@code mismatch}, the members are rendered individually and only those for which {@code isBadMember} is true are
     * highlighted — so only the part of the union that actually fails stands out. Otherwise the whole value is
     * rendered (highlighted iff {@code mismatch}).
     */
    private static Cell unionAwareCell(@Nullable PyType type,
                                       String typeName,
                                       boolean mismatch,
                                       TypeEvalContext context,
                                       boolean alignRight,
                                       String suffix,
                                       Predicate<@Nullable PyType> isBadMember) {
        Collection<@Nullable PyType> members = type instanceof PyUnionType unionType ? unionType.getMembers() : null;
        if (!mismatch || members == null || members.size() < 2) {
            return PyTypeDiffGrid.typeValue(type, typeName, mismatch, alignRight, suffix);
        }
        List<PyTypeDiffGrid.Segment> segments = new ArrayList<>();
        int i = 0;
        for (PyType member : members) {
            if (i > 0) {
                segments.add(PyTypeDiffGrid.segmentDelim(" | "));
            }
            String name = bareTypeName(member, context);
            segments.add(PyTypeDiffGrid.typeSegment(member, name, isBadMember.test(member)));
            i++;
        }
        return PyTypeDiffGrid.segmented(segments, alignRight, suffix);
    }

    private static @Nullable List<Col> tupleColumns(@Nullable PyType expected,
                                                    @Nullable PyType actual,
                                                    TypeEvalContext context,
                                                    Variance variance,
                                                    int depth) {
        if (!(expected instanceof PyTupleType expectedTuple) || !(actual instanceof PyTupleType actualTuple)) {
            return null;
        }
        // A named tuple is shown by its own name, so its fields are not aligned as the elements of a plain `tuple`.
        if (expected instanceof PyNamedTupleType || actual instanceof PyNamedTupleType) {
            return null;
        }
        if (expectedTuple.isHomogeneous() || actualTuple.isHomogeneous()) {
            return null;
        }
        if (expectedTuple.getElementCount() != actualTuple.getElementCount() || expectedTuple.getElementCount() == 0) {
            return null;
        }
        // A tuple is covariant in its elements, so each element keeps the tuple's own variance.
        return bracketed("tuple", "tuple",
            argumentColumns(expectedTuple.getElementTypes(), actualTuple.getElementTypes(), context, depth, i -> variance),
            false, actual, expected);
    }

    private static @Nullable List<Col> genericColumns(@Nullable PyType expected,
                                                      @Nullable PyType actual,
                                                      TypeEvalContext context,
                                                      Variance variance,
                                                      int depth) {
        // Tuples are a `PyClassType` too, but they're aligned by [tupleColumns]; a tuple reaching here was
        // rejected there (homogeneous, or mismatched arity), so it must stay a flat leaf — not be decomposed as a
        // 1-argument generic (which would drop a homogeneous tuple's `...`). A named tuple has type arguments of its class.
        if ((expected instanceof PyTupleType && !(expected instanceof PyNamedTupleType)) ||
            (actual instanceof PyTupleType && !(actual instanceof PyNamedTupleType))) {
            return null;
        }
        if (!(expected instanceof PyClassType expectedClass)) {
            return null;
        }
        if (!(actual instanceof PyClassType actualClass)) {
            return null;
        }
        // The type arguments of a named tuple belong to its own class. They do not align with those of another class.
        if ((expected instanceof PyNamedTupleType || actual instanceof PyNamedTupleType) &&
            !expectedClass.getPyClass().equals(actualClass.getPyClass())) {
            return null;
        }
        List<PyType> expectedArgs = expectedClass.getTypeArguments();
        List<PyType> actualArgs = actualClass.getTypeArguments();
        if (expectedArgs.isEmpty() && actualArgs.isEmpty()) {
            return null;
        }
        // Only align type arguments when the two origins are related — the same class, or one a subclass of the other
        // (either direction; the per-argument variance below decides what is actually incompatible). Otherwise they
        // are unrelated and shown as a single red leaf pair.
        if (!actualClass.getPyClass().isSubclass(expectedClass.getPyClass(), context) &&
            !expectedClass.getPyClass().isSubclass(actualClass.getPyClass(), context)) {
            return null;
        }
        String expectedName = expectedClass.getPyClass().getName();
        if (expectedName == null) {
            return null;
        }
        String actualName = actualClass.getPyClass().getName();
        if (actualName == null) {
            return null;
        }
        // The container kinds themselves are incompatible when the actual class isn't a subclass of the expected one
        // (e.g. a `Sequence` assigned where a `list` is expected): then the base names are red too, not just the
        // differing type arguments. The reverse (a `list` where a `Sequence` is expected) is a fine container kind.
        boolean baseMismatch = !actualClass.getPyClass().isSubclass(expectedClass.getPyClass(), context);
        PyClassType definition = PyTypeChecker.findGenericDefinitionType(expectedClass.getPyClass(), context);
        // A `ParamSpec` generic (`A[**P]`) is parameterized by a parameter list, so its arguments are rendered
        // exactly like a callable's parameters (parens, aligned names, per-type decomposition) rather than as plain
        // type arguments. `P` is invariant in the class, so the parameters are compared invariantly.
        if (definition != null && definition.getTypeArguments().stream().anyMatch(it -> it instanceof PyParamSpecType)) {
            List<Col> paramColumns = parameterListColumns(paramSpecParameters(expectedArgs), paramSpecParameters(actualArgs), context,
                Variance.INVARIANT, depth);
            return bracketed(actualName, expectedName, paramColumns, baseMismatch, actualClass, expectedClass);
        }
        // Same arity: align type arguments one-to-one, each at the container position's variance composed with the
        // parameter's declared variance — so a `list` element (invariant) is invariant, `Sequence` (covariant) keeps
        // it, etc.
        List<Col> argColumns;
        if (expectedArgs.size() == actualArgs.size()) {
            argColumns = argumentColumns(expectedArgs, actualArgs, context, depth,
                i -> variance.then(declaredVariance(expectedClass, i, context)));
        }
        else {
            // Differing arity is only alignable for a variadic generic (parameterized by a `TypeVarTuple`): it absorbs
            // the differing middle run; any leftover argument is shown as extra (red).
            argColumns = variadicArgColumns(definition, expectedClass, expectedArgs, actualArgs, variance, context, depth);
            if (argColumns == null) {
                return null;
            }
        }
        return bracketed(actualName, expectedName, argColumns, baseMismatch, actualClass, expectedClass);
    }

    /**
     * The parameters captured by a {@code ParamSpec} argument: a {@code [a, b]}-style list contributes its parameters
     * directly; a bare argument list ({@code A[int, str]}) is a series of anonymous positional parameters.
     */
    private static List<PyCallableParameter> paramSpecParameters(List<PyType> args) {
        if (args.size() == 1 && args.get(0) instanceof PyCallableParameterListType parameterListType) {
            return parameterListType.getParameters();
        }
        List<PyCallableParameter> result = new ArrayList<>();
        for (PyType arg : args) {
            result.add(PyCallableParameterImpl.nonPsi(arg));
        }
        return result;
    }

    /**
     * One aligned generic type-argument: present on each side or not (a {@code TypeVarTuple} may absorb more arguments
     * on one side than the other, leaving extras with no counterpart).
     */
    private record ArgSlot(@Nullable PyType actual,
                           @Nullable PyType expected,
                           boolean hasActual,
                           boolean hasExpected,
                           Variance variance) {
    }

    /**
     * The column(s) for the type arguments of a variadic generic whose two instantiations have different arity.
     * Returns {@code null} when the generic isn't actually variadic (no {@code TypeVarTuple} in its {@code definition}) and so the
     * differing arity can't be aligned. The arguments fixed before/after the {@code TypeVarTuple} are matched up
     * positionally (from the left and from the right respectively); the {@code TypeVarTuple} itself absorbs the run in
     * between, aligned from the left, with any surplus argument on either side shown as an extra red cell.
     */
    private static @Nullable List<Col> variadicArgColumns(@Nullable PyClassType definition,
                                                          PyClassType generic,
                                                          List<PyType> expectedArgs,
                                                          List<PyType> actualArgs,
                                                          Variance variance,
                                                          TypeEvalContext context,
                                                          int depth) {
        if (definition == null) {
            return null;
        }
        // The TypeVarTuple's index is exactly the count of fixed parameters before it.
        List<PyType> definitionArgs = definition.getTypeArguments();
        int fixedLeft = -1;
        for (int k = 0; k < definitionArgs.size(); k++) {
            if (definitionArgs.get(k) instanceof PyTypeVarTupleType) {
                fixedLeft = k;
                break;
            }
        }
        if (fixedLeft < 0) {
            return null;
        }
        int fixedRight = definitionArgs.size() - fixedLeft - 1;
        if (expectedArgs.size() < fixedLeft + fixedRight || actualArgs.size() < fixedLeft + fixedRight) {
            return null;
        }

        List<ArgSlot> slots = new ArrayList<>();
        for (int i = 0; i < fixedLeft; i++) {
            slots.add(new ArgSlot(actualArgs.get(i), expectedArgs.get(i), true, true, variance.then(declaredVariance(generic, i, context))));
        }
        List<PyType> actualMiddle = actualArgs.subList(fixedLeft, actualArgs.size() - fixedRight);
        List<PyType> expectedMiddle = expectedArgs.subList(fixedLeft, expectedArgs.size() - fixedRight);
        // The variadic parameter is invariant, so its absorbed elements are too.
        for (int m = 0; m < Math.max(actualMiddle.size(), expectedMiddle.size()); m++) {
            slots.add(new ArgSlot(getOrNull(actualMiddle, m), getOrNull(expectedMiddle, m), m < actualMiddle.size(), m < expectedMiddle.size(),
                Variance.INVARIANT));
        }
        for (int r = 0; r < fixedRight; r++) {
            int definitionIndex = fixedLeft + 1 + r;
            slots.add(new ArgSlot(actualArgs.get(actualArgs.size() - fixedRight + r), expectedArgs.get(expectedArgs.size() - fixedRight + r), true,
                true, variance.then(declaredVariance(generic, definitionIndex, context))));
        }

        List<Col> columns = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            List<Col> elementColumns = renderArgSlot(slots.get(index), context, depth + 1);
            columns.addAll(index < slots.size() - 1 ? withTrailingComma(elementColumns) : elementColumns);
        }
        return columns;
    }

    private static List<Col> renderArgSlot(ArgSlot slot, TypeEvalContext context, int depth) {
        if (slot.hasActual() && slot.hasExpected()) {
            return columns(slot.expected(), slot.actual(), context, slot.variance(), depth);
        }
        // A surplus argument on one side has no counterpart, so it is the incompatibility: shown red, while the side
        // that lacks it gets an empty mismatch cell the grid paints as a missing-position block across the column.
        Cell actualCell = slot.hasActual()
            ? PyTypeDiffGrid.typeValue(slot.actual(), bareTypeName(slot.actual(), context), true)
            : PyTypeDiffGrid.value("", true);
        Cell expectedCell = slot.hasExpected()
            ? PyTypeDiffGrid.typeValue(slot.expected(), bareTypeName(slot.expected(), context), true)
            : PyTypeDiffGrid.value("", true);
        return List.of(new Col(actualCell, expectedCell));
    }

    /**
     * Wraps inner type-argument {@code inner} columns with the base name and brackets: {@code name[ … ]}. The base name is a
     * type name, so it gets the normal (value) color — never muted; only the brackets and commas are muted
     * delimiters. It is highlighted red on both sides when {@code baseMismatch} — the container kinds themselves are
     * incompatible (e.g. a {@code Sequence} where a {@code list} is expected), not just their type arguments.
     */
    private static List<Col> bracketed(String actualName,
                                       String expectedName,
                                       List<Col> inner,
                                       boolean baseMismatch,
                                       @Nullable PyType actualType,
                                       @Nullable PyType expectedType) {
        List<Col> result = new ArrayList<>();
        result.add(new Col(PyTypeDiffGrid.typeValue(actualType, actualName, baseMismatch),
            PyTypeDiffGrid.typeValue(expectedType, expectedName, baseMismatch)));
        result.add(delimColumn("["));
        result.addAll(inner);
        result.add(delimColumn("]"));
        return result;
    }

    /**
     * The inner aligned columns (with separating commas) for same-arity type arguments — without the surrounding
     * base name or brackets. Each argument is recursed into at the variance given by {@code elementVariance}.
     */
    private static List<Col> argumentColumns(List<PyType> expectedArgs,
                                             List<PyType> actualArgs,
                                             TypeEvalContext context,
                                             int depth,
                                             IntFunction<Variance> elementVariance) {
        List<Col> columns = new ArrayList<>();
        for (int i = 0; i < expectedArgs.size(); i++) {
            List<Col> elementColumns = columns(expectedArgs.get(i), actualArgs.get(i), context, elementVariance.apply(i), depth + 1);
            // The comma after an element hugs it (and the padding falls after the comma), so attach it as a suffix
            // of the element's last column rather than as a separate column.
            columns.addAll(i < expectedArgs.size() - 1 ? withTrailingComma(elementColumns) : elementColumns);
        }
        return columns;
    }

    private static List<Col> withTrailingComma(List<Col> elementColumns) {
        if (elementColumns.isEmpty()) {
            return elementColumns;
        }
        Col last = elementColumns.get(elementColumns.size() - 1);
        Col withComma = new Col(PyTypeDiffGrid.withSuffix(last.actual(), ", "), PyTypeDiffGrid.withSuffix(last.expected(), ", "));
        List<Col> result = new ArrayList<>(elementColumns.subList(0, elementColumns.size() - 1));
        result.add(withComma);
        return result;
    }

    // ---- Callable layout ----------------------------------------------------------------------------------------

    private static @Nullable List<Col> callableColumns(PyCallableType expected,
                                                       PyCallableType actual,
                                                       TypeEvalContext context,
                                                       Variance variance,
                                                       int depth) {
        List<PyCallableParameter> expectedParameters = expected.getParameters(context);
        if (expectedParameters == null) {
            return null;
        }
        List<PyCallableParameter> actualParameters = actual.getParameters(context);
        if (actualParameters == null) {
            return null;
        }

        PyFunction expectedFunction = callableFunction(expected);
        PyFunction actualFunction = callableFunction(actual);
        boolean expectedAsync = expectedFunction != null && expectedFunction.isAsync();
        boolean actualAsync = actualFunction != null && actualFunction.isAsync();
        // Only one side is an `async def`, but its coroutine is compatible with the other side's return type (for example
        // `Callable[..., Awaitable[int]]`). The source form would then show a false mismatch, so compare the real types.
        if (expectedAsync != actualAsync &&
            !typesMismatch(actual.getReturnType(context), expected.getReturnType(context), variance, context)) {
            expectedAsync = false;
            actualAsync = false;
        }

        List<Col> columns = new ArrayList<>();
        // The `async` modifier, in source form, on the side(s) whose callable is a coroutine function — highlighted
        // when only one side has it, since an `async def` isn't assignable where a plain callable is expected.
        if (expectedAsync || actualAsync) {
            boolean mismatch = expectedAsync != actualAsync;
            columns.add(new Col(asyncModifierCell(actualAsync, mismatch), asyncModifierCell(expectedAsync, mismatch)));
        }
        // The leading `[T, …]` type-parameter list, in source form, so a generic function reads `[T](T) -> …`; when both
        // sides are generic a differing name or bound is highlighted position-by-position.
        columns.addAll(callableTypeParameterColumns(expectedFunction != null ? expectedFunction.getTypeParameterList() : null,
            actualFunction != null ? actualFunction.getTypeParameterList() : null, context));

        // Parameters are contravariant relative to the callable's own position; the return type keeps it. For an async
        // function the return is shown as its source form (`int`), not the desugared `Coroutine[Any, Any, int]`.
        Variance parameterVariance = variance.then(Variance.CONTRAVARIANT);
        columns.addAll(parameterListColumns(expectedParameters, actualParameters, context, parameterVariance, depth));
        columns.add(delimColumn(" -> "));
        columns.addAll(columns(sourceReturnType(expected, expectedAsync, context), sourceReturnType(actual, actualAsync, context), context,
            variance, depth + 1));
        return columns;
    }

    /**
     * The declaring function behind {@code type}, when it is a plain {@code def}/{@code async def} (not a synthesized {@code Callable[…]}
     * or a lambda) — the source of the {@code async} modifier and the type-parameter list.
     */
    private static @Nullable PyFunction callableFunction(PyCallableType type) {
        return type instanceof PyFunctionType functionType && functionType.getCallable() instanceof PyFunction function ? function : null;
    }

    /**
     * The return type in source form: for an {@code async def} the awaited result ({@code int}), not the desugared coroutine
     * ({@link PyCallableType#getReturnType} yields {@code Coroutine[Any, Any, int]}); everything else is returned unchanged.
     */
    private static @Nullable PyType sourceReturnType(PyCallableType type, boolean isAsync, TypeEvalContext context) {
        PyType returnType = type.getReturnType(context);
        if (isAsync) {
            Ref<PyType> unwrapped = PyTypingTypeProvider.unwrapCoroutineReturnType(returnType);
            PyType awaited = unwrapped != null ? unwrapped.get() : null;
            return awaited != null ? awaited : returnType;
        }
        return returnType;
    }

    private static Cell asyncModifierCell(boolean present, boolean mismatch) {
        return present ? PyTypeDiffGrid.value("async ", mismatch) : PyTypeDiffGrid.value("", false);
    }

    /**
     * The leading {@code [T, …]} type-parameter group for the two callables. When only one side is generic there is nothing
     * to align, so its list is rendered as a plain (unhighlighted) prefix on that side and the other side stays bare;
     * when both are generic they are aligned position-by-position (reusing {@link #typeParameterColumns}) so a differing name
     * or bound is highlighted.
     */
    private static List<Col> callableTypeParameterColumns(@Nullable PyTypeParameterList expected,
                                                          @Nullable PyTypeParameterList actual,
                                                          TypeEvalContext context) {
        List<PyTypeParameter> expectedParams = expected != null ? expected.getTypeParameters() : List.of();
        List<PyTypeParameter> actualParams = actual != null ? actual.getTypeParameters() : List.of();
        if (expectedParams.isEmpty() && actualParams.isEmpty()) {
            return List.of();
        }
        if (expectedParams.isEmpty() || actualParams.isEmpty()) {
            return List.of(new Col(plainTypeParameterList(actualParams, context), plainTypeParameterList(expectedParams, context)));
        }
        return typeParameterColumns(expected, actual, context);
    }

    /**
     * A whole type-parameter list {@code [T, U: bound]} rendered as one plain, unhighlighted cell (empty when there are none).
     */
    private static Cell plainTypeParameterList(List<PyTypeParameter> params, TypeEvalContext context) {
        if (params.isEmpty()) {
            return PyTypeDiffGrid.value("", false);
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (PyTypeParameter param : params) {
            joiner.add(typeParameterSource(param, context));
        }
        return PyTypeDiffGrid.value(joiner.toString(), false);
    }

    /**
     * A single type parameter in source form: its name, plus {@code : bound} when it is bounded.
     */
    private static String typeParameterSource(PyTypeParameter param, TypeEvalContext context) {
        String name = param.getName() != null ? param.getName() : "";
        PyType bound = typeParameterBound(param, context);
        if (bound == null) {
            return name;
        }
        return name + ": " + bareTypeName(bound, context);
    }

    private static List<Col> parameterListColumns(List<PyCallableParameter> expectedParameters,
                                                  List<PyCallableParameter> actualParameters,
                                                  TypeEvalContext context,
                                                  Variance variance,
                                                  int depth) {
        Layout expectedLayout = layout(expectedParameters, context);
        Layout actualLayout = layout(actualParameters, context);
        // When the expected signature names none of its parameters (e.g. a bare `Callable[[str, int], int]`), the
        // actual parameter names are just noise on the rows that already line up — so they are hidden, except on the
        // parameters that actually differ, where the name still helps the reader spot which one is wrong.
        boolean hideMatchedNames = !expectedLayout.columns().isEmpty() && expectedLayout.columns().stream().allMatch(it -> it.rawName == null);

        List<Col> columns = new ArrayList<>();
        columns.add(new Col(PyTypeDiffGrid.delim("(" + actualLayout.leading()), PyTypeDiffGrid.delim("(" + expectedLayout.leading())));
        for (Slot slot : alignParameters(actualLayout.columns(), expectedLayout.columns(), context, variance)) {
            columns.addAll(renderSlot(slot, hideMatchedNames, context, variance, depth));
        }
        columns.add(delimColumn(")"));
        return columns;
    }

    /**
     * One aligned grid slot: the actual-side and expected-side parameter shown on it. Either may be {@code null} for
     * padding, which happens when a {@code *args}/{@code **kwargs} container on one side absorbs several parameters from the
     * other: the container is rendered once and the parameters it absorbs are expanded opposite empty cells.
     * <p>
     * {@code actualTypeMismatch}/{@code expectedTypeMismatch} are tracked per side because an absorbing container reds as a
     * whole when it rejects <i>any</i> of the parameters it absorbs, while each absorbed parameter reds on its own.
     * {@code expectedAgainst} is the actual-side type the expected cell is checked against (for per-union-member
     * highlighting) — the absorbing container's element type on the cells it absorbs.
     * <p>
     * {@code actualMissing}/{@code expectedMissing} mark a one-to-one slot where that side has no parameter at all while the
     * other does (the provided signature lacks a parameter the expected one mandates, or vice versa). The empty
     * side is then painted as a visible "missing position" background block rather than an invisible empty cell.
     * They are never set on a container's absorbed-padding slots, whose empty side is intentional, not missing.
     */
    private static final class Slot {
        final @Nullable ParamColumn actual;
        final @Nullable ParamColumn expected;
        final boolean actualTypeMismatch;
        final boolean expectedTypeMismatch;
        final @Nullable PyType expectedAgainst;
        final boolean actualMissing;
        final boolean expectedMissing;

        Slot(@Nullable ParamColumn actual,
             @Nullable ParamColumn expected,
             boolean actualTypeMismatch,
             boolean expectedTypeMismatch,
             @Nullable PyType expectedAgainst) {
            this(actual, expected, actualTypeMismatch, expectedTypeMismatch, expectedAgainst, false, false);
        }

        Slot(@Nullable ParamColumn actual,
             @Nullable ParamColumn expected,
             boolean actualTypeMismatch,
             boolean expectedTypeMismatch,
             @Nullable PyType expectedAgainst,
             boolean actualMissing,
             boolean expectedMissing) {
            this.actual = actual;
            this.expected = expected;
            this.actualTypeMismatch = actualTypeMismatch;
            this.expectedTypeMismatch = expectedTypeMismatch;
            this.expectedAgainst = expectedAgainst;
            this.actualMissing = actualMissing;
            this.expectedMissing = expectedMissing;
            PyAnyType.validate(expectedAgainst);
        }
    }

    /**
     * Aligns two parameter lists into {@link Slot}s, mirroring the spec's callable-compatibility mapping but tolerant of
     * mismatches (it keeps going past them so the whole diff can be shown). A {@code *args}/{@code **kwargs} container on one
     * side absorbs the run of plain parameters opposite it — each checked against the container's element type —
     * plus the other side's matching container, if any; the container is shown once and the absorbed parameters
     * are expanded against empty padding cells. Everything else is aligned one-to-one.
     */
    private static List<Slot> alignParameters(List<ParamColumn> actual, List<ParamColumn> expected, TypeEvalContext context, Variance variance) {
        List<Slot> slots = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < actual.size() || j < expected.size()) {
            ParamColumn actualParam = getOrNull(actual, i);
            ParamColumn expectedParam = getOrNull(expected, j);
            if (actualParam != null && actualParam.container && expectedParam != null && !expectedParam.container) {
                // An actual `*args`/`**kwargs` absorbs the opposite run of plain parameters (each checked against the
                // container's element type) plus the expected container, if present. It is shown on the first slot; the
                // absorbed parameters are expanded against empty actual cells on the following slots.
                List<ParamColumn> absorbed = new ArrayList<>();
                while (j < expected.size() && !expected.get(j).container) {
                    absorbed.add(expected.get(j++));
                }
                if (j < expected.size()) {
                    absorbed.add(expected.get(j++));
                }
                boolean containerReds = absorbed.stream().anyMatch(it -> typesMismatch(actualParam.matchType, it.matchType, variance, context));
                for (int index = 0; index < absorbed.size(); index++) {
                    ParamColumn e = absorbed.get(index);
                    boolean reds = typesMismatch(actualParam.matchType, e.matchType, variance, context);
                    slots.add(new Slot(index == 0 ? actualParam : null, e, index == 0 ? containerReds : false, reds, actualParam.matchType));
                }
                i++;
            }
            else if (expectedParam != null && expectedParam.container && actualParam != null && !actualParam.container) {
                // Symmetric: an expected `*args`/`**kwargs` absorbs the opposite run of actual parameters.
                List<ParamColumn> absorbed = new ArrayList<>();
                while (i < actual.size() && !actual.get(i).container) {
                    absorbed.add(actual.get(i++));
                }
                if (i < actual.size()) {
                    absorbed.add(actual.get(i++));
                }
                boolean containerReds = absorbed.stream().anyMatch(it -> typesMismatch(it.matchType, expectedParam.matchType, variance, context));
                for (int index = 0; index < absorbed.size(); index++) {
                    ParamColumn a = absorbed.get(index);
                    boolean reds = typesMismatch(a.matchType, expectedParam.matchType, variance, context);
                    slots.add(new Slot(a, index == 0 ? expectedParam : null, reds, index == 0 ? containerReds : false, expectedParam.matchType));
                }
                j++;
            }
            else {
                boolean mismatch = isTypeMismatch(actualParam, expectedParam, variance, context);
                slots.add(new Slot(actualParam, expectedParam, mismatch, mismatch,
                    actualParam != null ? actualParam.matchType : PyAnyType.getUnknown(),
                    actualParam == null, expectedParam == null));
                if (actualParam != null) {
                    i++;
                }
                if (expectedParam != null) {
                    j++;
                }
            }
        }
        return slots;
    }

    /**
     * Renders one aligned {@code slot} as its name, type and default grid columns.
     */
    private static List<Col> renderSlot(Slot slot, boolean hideMatchedNames, TypeEvalContext context, Variance variance, int depth) {
        ParamColumn actual = slot.actual;
        ParamColumn expected = slot.expected;
        // A name is the incompatibility when keyword-callable parameters disagree on it, when one side can't be passed
        // the way the other requires (a keyword-only vs. positional-only parameter — see [isKindMismatch]), or when this
        // is a surplus/missing parameter — one side has it, the other doesn't: the WHOLE parameter is the mismatch, so
        // its name is highlighted alongside its type (e.g. a provided `a: int` where no parameter is expected reds both).
        boolean nameMismatch = isNameMismatch(actual, expected) || isKindMismatch(actual, expected) ||
            slot.actualMissing || slot.expectedMissing;
        boolean defaultMismatch = isDefaultMismatch(actual, expected);
        boolean showName = !hideMatchedNames || slot.actualTypeMismatch || slot.expectedTypeMismatch || defaultMismatch || nameMismatch;
        // Name part: right-aligned so the `: type` parts line up vertically across both rows — EXCEPT when one side is
        // typed and the other isn't (a base `self: int` overridden by a bare `self`), where right-alignment would shove
        // the bare name under the other's `: `; there both are left-aligned so the identifiers line up instead. It is
        // highlighted only when the names themselves are the incompatibility (keyword-callable parameters must share a
        // name). An anonymous or positional-only parameter opposite a named one has no name to color, so its empty cell
        // is rendered as a missing-position gap by the grid (see [PyTypeDiffGrid.line]), making the absent name visible.
        boolean typeAsymmetric = actual != null && expected != null && actual.type.isEmpty() != expected.type.isEmpty();
        Col nameColumn = new Col(
            PyTypeDiffGrid.value(nameText(actual, hideMatchedNames, showName), nameMismatch, !typeAsymmetric),
            PyTypeDiffGrid.value(nameText(expected, hideMatchedNames, showName), nameMismatch, !typeAsymmetric)
        );
        List<Col> typeColumns = parameterTypeColumns(slot, context, variance, depth);
        // The ` = ...` default is its own cell so it can be highlighted on its own (a parameter that drops a
        // required default is incompatible even when the types match). The comma/separators hug it as a suffix.
        Col defaultColumn = new Col(
            PyTypeDiffGrid.value(actual != null ? actual.defaultText : "", defaultMismatch, false, actual != null ? actual.suffix : ""),
            PyTypeDiffGrid.value(expected != null ? expected.defaultText : "", defaultMismatch, false, expected != null ? expected.suffix : "")
        );
        List<Col> columns = new ArrayList<>();
        columns.add(nameColumn);
        columns.addAll(typeColumns);
        columns.add(defaultColumn);
        // An absent parameter on one side is empty in every column; mark those cells as a mismatch so the grid paints
        // each as a missing-position gap across the column the other side occupies.
        if (slot.actualMissing) {
            List<Col> result = new ArrayList<>();
            for (Col it : columns) {
                result.add(new Col(PyTypeDiffGrid.value("", true), it.expected()));
            }
            return result;
        }
        if (slot.expectedMissing) {
            List<Col> result = new ArrayList<>();
            for (Col it : columns) {
                result.add(new Col(it.actual(), PyTypeDiffGrid.value("", true)));
            }
            return result;
        }
        return columns;
    }

    /**
     * The grid column(s) for a parameter's type at the given {@code variance} (usually contravariant, but e.g. invariant
     * for a parameter of a {@code Callable} nested in a {@code list}). When both sides share structure (a generic or tuple
     * whose element types differ, e.g. a {@code list} of {@code int} vs a {@code list} of {@code str}) the type is decomposed so only the
     * differing sub-part is highlighted — the shared wrapper stays plain. This applies to a {@code *args}/{@code **kwargs}
     * container too: its element type is decomposed against the parameter opposite it. Otherwise it is a single
     * cell: when contravariant the expected type is the source whose union members may individually fail and the
     * actual type is the whole acceptor; otherwise both sides are shown whole.
     */
    private static List<Col> parameterTypeColumns(Slot slot, TypeEvalContext context, Variance variance, int depth) {
        ParamColumn actual = slot.actual;
        ParamColumn expected = slot.expected;
        // Only decompose when both sides actually show a type. A hidden type (an implicit `self`, rendered as the bare
        // name) has nothing to align structurally — its real type is kept only to detect the mismatch, never to display.
        if (actual != null && expected != null && !actual.type.isEmpty() && !expected.type.isEmpty()) {
            List<Col> structured = structuredColumns(expected.matchType, actual.matchType, context, variance, depth + 1);
            if (structured != null) {
                return structured;
            }
        }
        Cell expectedTypeCell;
        if (expected != null && expected.container) {
            expectedTypeCell = PyTypeDiffGrid.typeValue(expected.matchType, expected.type, slot.expectedTypeMismatch);
        }
        else if (variance == Variance.CONTRAVARIANT) {
            expectedTypeCell = unionAwareCell(expected != null ? expected.matchType : null, expected != null ? expected.type : "",
                slot.expectedTypeMismatch, context, false, "", it -> !PyTypeChecker.match(slot.expectedAgainst, it, context));
        }
        else {
            expectedTypeCell = PyTypeDiffGrid.typeValue(expected != null ? expected.matchType : null, expected != null ? expected.type : "",
                slot.expectedTypeMismatch);
        }
        return List.of(new Col(PyTypeDiffGrid.typeValue(actual != null ? actual.matchType : null, actual != null ? actual.type : "",
            slot.actualTypeMismatch), expectedTypeCell));
    }

    /**
     * The text for a parameter's name cell. A {@code *args}/{@code **kwargs} marker is structural, not just a name, so it is
     * always shown — but when names are hidden its identifier is dropped, leaving just {@code *}/{@code **} (with a trailing
     * {@code : } when the parameter is typed). Ordinary parameter names are shown only when {@code showName}.
     */
    private static String nameText(@Nullable ParamColumn column, boolean hideMatchedNames, boolean showName) {
        if (column == null) {
            return "";
        }
        if (column.container) {
            return hideMatchedNames ? column.prefix + (!column.type.isEmpty() ? ": " : "") : column.name;
        }
        return showName ? column.name : "";
    }

    /**
     * A parameter type is incompatible when it fails the assignability the {@code variance} requires (usually
     * contravariant — the actual parameter must accept the expected one). A missing side is always a mismatch.
     */
    private static boolean isTypeMismatch(@Nullable ParamColumn actual, @Nullable ParamColumn expected, Variance variance, TypeEvalContext context) {
        if (actual == null && expected == null) {
            return false;
        }
        if (actual == null || expected == null) {
            return true;
        }
        // Two implicit `self`/`cls` receivers differ legitimately (`Self@A` vs `Self@B`) — never flag that. An explicit
        // annotation on either side IS a real constraint, so there it is compared against the other side's real receiver.
        if (actual.selfImplicit && expected.selfImplicit) {
            return false;
        }
        return typesMismatch(actual.matchType, expected.matchType, variance, context);
    }

    /**
     * The actual must keep any default the expected one has — otherwise it requires an argument the expected
     * signature treats as optional, which makes it unassignable even when the types match.
     */
    private static boolean isDefaultMismatch(@Nullable ParamColumn actual, @Nullable ParamColumn expected) {
        return actual != null && expected != null && expected.hasDefault && !actual.hasDefault;
    }

    /**
     * Keyword-callable parameters must share a name. Positional-only ({@code /}) and anonymous parameters are matched
     * by position, so a name difference there is not an incompatibility.
     */
    private static boolean isNameMismatch(@Nullable ParamColumn actual, @Nullable ParamColumn expected) {
        if (actual == null || expected == null) {
            return false;
        }
        if (actual.positionalOnly || expected.positionalOnly) {
            return false;
        }
        String actualName = actual.rawName;
        if (actualName == null) {
            return false;
        }
        String expectedName = expected.rawName;
        if (expectedName == null) {
            return false;
        }
        return !actualName.equals(expectedName);
    }

    /**
     * The actual must accept every calling convention the expected one supports: if the expected parameter can be
     * passed positionally the actual must accept a positional argument (a keyword-only {@code *, a} can't), and if the
     * expected one can be passed by keyword the actual must accept it by name (a positional-only {@code a, /} can't).
     * Either way the parameter doesn't match because of how it's named/passed, even when the types agree.
     * Containers are aligned by absorption, not here.
     */
    private static boolean isKindMismatch(@Nullable ParamColumn actual, @Nullable ParamColumn expected) {
        if (actual == null || expected == null || actual.container || expected.container) {
            return false;
        }
        boolean positionalBridgeFails = expected.acceptsPositional() && !actual.acceptsPositional();
        boolean keywordBridgeFails = expected.acceptsKeyword() && !actual.acceptsKeyword();
        return positionalBridgeFails || keywordBridgeFails;
    }

    /**
     * One aligned parameter column: a (right-aligned) name part, a (left-aligned) type part, a {@code = ...} default
     * part, and a trailing run of commas and {@code /}/{@code *} separators.
     */
    private static final class ParamColumn {
        final String name;
        final String prefix;
        final String type;
        final String defaultText;
        final String suffix;
        final @Nullable PyType matchType;
        final boolean hasDefault;
        final @Nullable String rawName;
        final boolean positionalOnly;
        final boolean keywordOnly;
        final boolean container;
        /**
         * A {@code self}/{@code cls} receiver with no explicit annotation: its (implicit) type is hidden, and two such receivers
         * are never compared ({@code Self@A} vs {@code Self@B} differs by design).
         */
        final boolean selfImplicit;

        ParamColumn(String name,
                    String prefix,
                    String type,
                    String defaultText,
                    String suffix,
                    @Nullable PyType matchType,
                    boolean hasDefault,
                    @Nullable String rawName,
                    boolean positionalOnly,
                    boolean keywordOnly,
                    boolean container,
                    boolean selfImplicit) {
            this.name = name;
            this.prefix = prefix;
            this.type = type;
            this.defaultText = defaultText;
            this.suffix = suffix;
            this.matchType = matchType;
            this.hasDefault = hasDefault;
            this.rawName = rawName;
            this.positionalOnly = positionalOnly;
            this.keywordOnly = keywordOnly;
            this.container = container;
            this.selfImplicit = selfImplicit;
            PyAnyType.validate(matchType);
        }

        /**
         * A keyword-only parameter (after a bare {@code *} or {@code *args}) can't be supplied positionally.
         */
        boolean acceptsPositional() {
            return !keywordOnly;
        }

        /**
         * A positional-only ({@code /} or anonymous) parameter has no name a caller can pass it by.
         */
        boolean acceptsKeyword() {
            return rawName != null && !positionalOnly;
        }
    }

    private record Layout(String leading, List<ParamColumn> columns) {
    }

    private static final class RealColumn {
        final String name;
        final String prefix;
        final String type;
        final boolean hasDefault;
        final @Nullable PyType matchType;
        final @Nullable String rawName;
        boolean positionalOnly;
        final boolean keywordOnly;
        final boolean container;
        final boolean selfImplicit;
        final List<String> separatorsAfter;

        RealColumn(String name,
                   String prefix,
                   String type,
                   boolean hasDefault,
                   @Nullable PyType matchType,
                   @Nullable String rawName,
                   boolean positionalOnly,
                   boolean keywordOnly,
                   boolean container,
                   boolean selfImplicit,
                   List<String> separatorsAfter) {
            this.name = name;
            this.prefix = prefix;
            this.type = type;
            this.hasDefault = hasDefault;
            this.matchType = matchType;
            this.rawName = rawName;
            this.positionalOnly = positionalOnly;
            this.keywordOnly = keywordOnly;
            this.container = container;
            this.selfImplicit = selfImplicit;
            this.separatorsAfter = separatorsAfter;
        }
    }

    /**
     * Splits a parameter list into aligned columns. {@code /} and {@code *} separators do not become columns of their own —
     * they are folded into the trailing {@link ParamColumn#suffix} of the preceding real parameter (or into
     * {@link Layout#leading} when no parameter precedes them) so the real parameters keep lining up across both signatures.
     */
    private static Layout layout(List<PyCallableParameter> parameters, TypeEvalContext context) {
        List<RealColumn> realColumns = new ArrayList<>();
        List<String> leadingSeparators = new ArrayList<>();
        // Everything after a bare `*` (or after a `*args`) is keyword-only and so can't be supplied positionally.
        boolean keywordOnly = false;

        for (PyCallableParameter parameter : parameters) {
            String separator = separatorToken(parameter);
            if (separator != null) {
                if (realColumns.isEmpty()) {
                    leadingSeparators.add(separator);
                }
                else {
                    realColumns.get(realColumns.size() - 1).separatorsAfter.add(separator);
                }
                // Every parameter before a `/` is positional-only, so its name doesn't participate in matching.
                if (parameter.isPositionOnlySeparator()) {
                    for (RealColumn it : realColumns) {
                        it.positionalOnly = true;
                    }
                }
                if (parameter.isKeywordOnlySeparator()) {
                    keywordOnly = true;
                }
                continue;
            }
            PyType argumentType = parameter.getArgumentType(context);
            // The `self`/`cls` receiver's type is hidden unless EXPLICITLY annotated: an implicit receiver legitimately
            // differs between base and override (`Self@A` vs `Self@B`) and would just be noise. Its real type is still kept
            // in [ParamColumn.matchType] so that an explicit annotation on the OTHER side is compared (see [isTypeMismatch]).
            boolean selfImplicit = parameter.isSelf() &&
                !(parameter.getParameter() instanceof PyNamedParameter namedParameter && namedParameter.getAnnotation() != null);
            String typeName = argumentType == null || selfImplicit ? "" : bareTypeName(argumentType, context);
            String prefix = PyMismatchTooltips.containerPrefix(parameter);
            String name = parameter.getName();

            String namePart;
            String typePart;
            if (name == null) {
                namePart = "";
                typePart = typeName;
            }
            else if (typeName.isEmpty()) {
                namePart = prefix + name;
                typePart = "";
            }
            else {
                namePart = prefix + name + ": ";
                typePart = typeName;
            }
            // A leading-double-underscore name is positional-only by convention, so it doesn't participate in
            // name matching either (mirrors PyCallableParameterMapping's categorization).
            boolean positionalOnly = parameter.getProtectionLevel() == ProtectionLevel.PRIVATE;
            realColumns.add(new RealColumn(namePart, prefix, typePart, parameter.hasDefaultValue(), argumentType, name, positionalOnly,
                keywordOnly, !prefix.isEmpty(), selfImplicit, new ArrayList<>()));
            // Parameters following a `*args` positional container are keyword-only too.
            if (parameter.isPositionalContainer()) {
                keywordOnly = true;
            }
        }

        List<ParamColumn> columns = new ArrayList<>();
        for (int i = 0; i < realColumns.size(); i++) {
            RealColumn column = realColumns.get(i);
            boolean isLast = i == realColumns.size() - 1;
            StringBuilder suffix = new StringBuilder();
            for (String it : column.separatorsAfter) {
                suffix.append(", ").append(it);
            }
            if (!isLast) {
                suffix.append(", ");
            }
            String defaultText = column.hasDefault ? " = ..." : "";
            columns.add(new ParamColumn(column.name, column.prefix, column.type, defaultText, suffix.toString(), column.matchType,
                column.hasDefault, column.rawName, column.positionalOnly, column.keywordOnly, column.container, column.selfImplicit));
        }
        StringBuilder leading = new StringBuilder();
        for (String it : leadingSeparators) {
            leading.append(it).append(", ");
        }
        return new Layout(leading.toString(), columns);
    }

    private static @Nullable String separatorToken(PyCallableParameter parameter) {
        if (parameter.isPositionOnlySeparator()) {
            return PyAstSlashParameter.TEXT;
        }
        if (parameter.isKeywordOnlySeparator()) {
            return PyAstSingleStarParameter.TEXT;
        }
        return null;
    }

    private static <T> @Nullable T getOrNull(List<T> list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }
}
