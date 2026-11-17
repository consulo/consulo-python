// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.impl.PyCustomType;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters.Counter;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PyTopType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.RecursionManager;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt.getProtocolMembers;
import static com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt.inspectProtocolSubclass;
import static com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt.isProtocol;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;

public final class PyTypeChecker {
    private PyTypeChecker() {
    }

    /**
     * The widest composite type that still gets a per-member breakdown. Past it, matching short-circuits and
     * records one summarizing reason. {@code 0} means no bound.
     */
    public static int maxBreakdownMembers() {
        return Registry.intValue("python.typing.composite.breakdown.max.members", 5);
    }

    /**
     * See {@link #match(PyType, PyType, TypeEvalContext, Map)} for description.
     */
    public static boolean match(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context) {
        GenericSubstitutions substitutions = new GenericSubstitutions();
        return match(expected, actual, new MatchContext(context, substitutions, false)).orElse(true);
    }

    /**
     * Checks whether a type {@code actual} can be placed where {@code expected} is expected.
     * <p>
     * <p>
     * For example {@code int} matches {@code object}, while {@code str} doesn't match {@code int}.
     * Work for builtin types, classes, tuples etc.
     * <p>
     * <p>
     * Whether it's unknown if {@code actual} match {@code expected} the method returns {@code true}.
     *
     * @param expected      expected type
     * @param actual        type to be matched against expected
     * @param context       type evaluation context
     * @param typeVars      map of substitutions for {@code expected} type
     * @return {@code false} if {@code expected} and {@code actual} don't match, true otherwise
     * @implNote This behavior may be changed in future by replacing {@code boolean} with {@code Optional<Boolean>} and updating the clients.
     */
    public static boolean match(@Nullable PyType expected,
                                @Nullable PyType actual,
                                TypeEvalContext context,
                                Map<PyTypeParameterType, @Nullable PyType> typeVars) {
        GenericSubstitutions substitutions = new GenericSubstitutions(typeVars);
        return match(expected, actual, new MatchContext(context, substitutions, false)).orElse(true);
    }

    public static boolean match(@Nullable PyType expected,
                                @Nullable PyType actual,
                                TypeEvalContext context,
                                GenericSubstitutions substitutions) {
        return match(expected, actual, new MatchContext(context, substitutions, false))
            .orElse(true);
    }

    public static @Nullable PyTypeMismatchExplanation explainMismatch(@Nullable PyType expected,
                                                                      @Nullable PyType actual,
                                                                      TypeEvalContext context) {
        return explainMismatch(expected, actual, context, null);
    }

    public static @Nullable PyTypeMismatchExplanation explainMismatch(@Nullable PyType expected,
                                                                      @Nullable PyType actual,
                                                                      TypeEvalContext context,
                                                                      @Nullable PsiElement anchor) {
        return explainMismatch(expected, actual, context, anchor, null);
    }

    /**
     * Re-runs the match between {@code expected} and {@code actual}, collecting a structured breakdown of <i>why</i>
     * they do not match (see {@link PyTypeMismatchExplanation}).
     * <p>
     * Returns {@code null} when the types actually match, or when the failing category has not been
     * instrumented (in which case the caller should fall back to a flat message). Collecting the
     * breakdown is comparatively expensive, so this is intentionally separate from {@link #match}: callers
     * run it only once a type error is about to be reported.
     * <p>
     * {@code substitutions}, when provided, is copied so the re-run starts from the same pre-bound state as
     * the original match without perturbing it.
     */
    public static @Nullable PyTypeMismatchExplanation explainMismatch(@Nullable PyType expected,
                                                                      @Nullable PyType actual,
                                                                      TypeEvalContext context,
                                                                      @Nullable PsiElement anchor,
                                                                      @Nullable GenericSubstitutions substitutions) {
        GenericSubstitutions baseSubstitutions = substitutions != null ? substitutions.addToCopy(null, null, null) : new GenericSubstitutions();
        MatchContext matchContext = new MatchContext(context, baseSubstitutions, false);
        DiagnosticsCollector collector = new DiagnosticsCollector();
        matchContext.diagnostics = collector;
        matchContext.anchor = anchor;
        boolean matched = match(expected, actual, matchContext).orElse(true);
        if (matched) {
            return null;
        }
        List<PyTypeMismatchExplanation> roots = collector.current;
        return switch (roots.size()) {
            case 0 -> null;
            case 1 -> roots.get(0);
            default -> new PyTypeMismatchExplanation(
                PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerTypeNotAssignable(params[0], params[1]),
                                                    codifiedType(matchContext, actual), codifiedType(matchContext, expected)),
                List.copyOf(roots),
                PyMismatchStep.Combined.INSTANCE
            );
        };
    }

    /**
     * Runs {@code body} and, only while a breakdown is being collected, records a node carrying {@code message}
     * whenever {@code body} reports a mismatch. The node's children are whatever nested frames {@code body}
     * recorded, so the resulting tree follows exactly the path that explains the failure; successful
     * or abandoned branches discard their buffer and leave no trace.
     * <p>
     * Inlined so there is genuinely no allocation when diagnostics are off (the common case).
     */
    private static Optional<Boolean> recordFrame(MatchContext context,
                                                 Supplier<ProblemMessage> message,
                                                 Supplier<@Nullable PyMismatchStep> step,
                                                 Supplier<Optional<Boolean>> body) {
        DiagnosticsCollector collector = context.diagnostics;
        if (collector == null) {
            return body.get();
        }
        List<PyTypeMismatchExplanation> parent = collector.current;
        List<PyTypeMismatchExplanation> child = new ArrayList<>();
        collector.current = child;
        Optional<Boolean> result;
        try {
            result = body.get();
        }
        finally {
            collector.current = parent;
        }
        if (!result.orElse(true)) {
            parent.add(new PyTypeMismatchExplanation(message.get(), List.copyOf(child), step.get()));
        }
        return result;
    }

    private static Optional<Boolean> recordFrame(MatchContext context,
                                                 Supplier<ProblemMessage> message,
                                                 Supplier<Optional<Boolean>> body) {
        return recordFrame(context, message, () -> null, body);
    }

    /**
     * {@link #recordFrame} for the many helpers that return a plain {@code boolean}.
     */
    private static boolean recordFrameBool(MatchContext context,
                                           Supplier<ProblemMessage> message,
                                           Supplier<@Nullable PyMismatchStep> step,
                                           BooleanSupplier body) {
        DiagnosticsCollector collector = context.diagnostics;
        if (collector == null) {
            return body.getAsBoolean();
        }
        Pair<Boolean, List<PyTypeMismatchExplanation>> collected = collectInto(collector, body::getAsBoolean);
        boolean result = collected.getFirst();
        if (!result) {
            collector.current.add(new PyTypeMismatchExplanation(message.get(), collected.getSecond(), step.get()));
        }
        return result;
    }

    /**
     * Runs {@code body} with recording diverted to a fresh buffer, and returns its result with what it recorded.
     * <p>
     * This lets a branch that explores several candidates choose the shape of its breakdown after the walk, so
     * no candidate is matched a second time just to record it. {@link #recordFrameBool} and {@link #withoutRecording} are the
     * two degenerate cases: attach the buffer to the parent, or drop it.
     */
    private static <T> Pair<T, List<PyTypeMismatchExplanation>> collectInto(DiagnosticsCollector collector, Supplier<T> body) {
        List<PyTypeMismatchExplanation> parent = collector.current;
        List<PyTypeMismatchExplanation> child = new ArrayList<>();
        collector.current = child;
        try {
            T result = body.get();
            return Pair.create(result, List.copyOf(child));
        }
        finally {
            collector.current = parent;
        }
    }

    /**
     * Records a terminal reason (no children) at the current frame, if a breakdown is being collected.
     */
    private static void recordLeaf(MatchContext context, Supplier<@Nullable PyMismatchStep> step, Supplier<ProblemMessage> message) {
        DiagnosticsCollector collector = context.diagnostics;
        if (collector == null) {
            return;
        }
        collector.current.add(new PyTypeMismatchExplanation(message.get(), Collections.emptyList(), step.get()));
    }

    private static void recordLeaf(MatchContext context, Supplier<ProblemMessage> message) {
        recordLeaf(context, () -> null, message);
    }

    /**
     * Runs {@code body} with recording diverted to a throwaway buffer, so nested matches leave no nodes.
     * Used when a branch explores many candidates (e.g. union members) and we want to emit a single
     * summarizing reason instead of one node per dead-end candidate.
     */
    private static <T> T withoutRecording(MatchContext context, Supplier<T> body) {
        DiagnosticsCollector collector = context.diagnostics;
        if (collector == null) {
            return body.get();
        }
        return collectInto(collector, body).getFirst();
    }

    /**
     * Runs {@code body} with normal short-circuiting and without creating nested explanations.
     */
    private static <T> T withoutDiagnostics(MatchContext context, Supplier<T> body) {
        DiagnosticsCollector collector = context.diagnostics;
        if (collector == null) {
            return body.get();
        }
        context.diagnostics = null;
        try {
            return body.get();
        }
        finally {
            context.diagnostics = collector;
        }
    }

    /**
     * Renders {@code type} as a {@link CodifiedParam} for a breakdown message: the description is the plain
     * type name (so {@link PyTypeMismatchExplanation#getMessage()}'s description stays the same text the Problems view would show),
     * and the tooltip is the same type with highlighted, clickable class links — resolved against {@link MatchContext#anchor}.
     * When no anchor is available (a programmatic {@link #explainMismatch} call), the tooltip falls back to a plain {@code <code>} span.
     */
    private static CodifiedParam codifiedType(MatchContext context, @Nullable PyType type) {
        PsiElement anchor = context.anchor;
        if (anchor != null) {
            return CodifiedParam.ofType(type, anchor, context.context);
        }
        String name = PythonDocumentationProvider.getTypeName(type, context.context);
        return new CodifiedParam(name, "<code>" + StringUtil.escapeXmlEntities(name) + "</code>");
    }

    /**
     * The covariant read-only supertype the user could annotate a target with to sidestep an invariant-container
     * mismatch: {@code list} -> {@code Sequence}, {@code set} -> {@code AbstractSet}, {@code dict} -> {@code Mapping}. Null for any other owner, so the
     * renderer only offers the hint where it is genuinely useful and correct. {@code Mapping} is invariant in its key type,
     * so it is offered only for the value type argument ({@code typeArgIndex} 1) of a {@code dict}.
     */
    private static @Nullable String readOnlyAlternativeFor(PyClass ownerClass, int typeArgIndex) {
        String qualifiedName = ownerClass.getQualifiedName();
        if ("builtins.list".equals(qualifiedName)) {
            return "Sequence";
        }
        if ("builtins.set".equals(qualifiedName)) {
            return "AbstractSet";
        }
        if ("builtins.dict".equals(qualifiedName)) {
            return typeArgIndex == 1 ? "Mapping" : null;
        }
        return null;
    }

    /**
     * The plain-text leaf message for a TypedDict key mismatch (the description; prose is built from the step).
     */
    private static ProblemMessage typedDictKeyMessage(PyTypedDictType.TypedDictKeyMismatch mismatch) {
        String key = mismatch.getKey();
        return switch (mismatch.getProblem()) {
            case MISSING -> PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownTypedDictKeyMissing(params[0]), key);
            case VALUE_TYPE -> PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownTypedDictKeyType(params[0]), key);
            case READONLY -> PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownTypedDictKeyReadonly(params[0]), key);
            case REQUIRED -> PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownTypedDictKeyRequired(params[0]), key);
        };
    }

    /**
     * The structured prose step for a TypedDict key mismatch, or null when the expected side isn't a TypedDict.
     */
    private static @Nullable PyMismatchStep typedDictStep(MatchContext context,
                                                          @Nullable PyType expected,
                                                          PyTypedDictType.@Nullable TypedDictKeyMismatch mismatch) {
        if (!(expected instanceof PyTypedDictType) || mismatch == null) {
            return null;
        }
        CodifiedParam actualValue = mismatch.getActualValue() != null ? codifiedType(context, mismatch.getActualValue()) : null;
        CodifiedParam expectedValue = mismatch.getExpectedValue() != null ? codifiedType(context, mismatch.getExpectedValue()) : null;
        return new PyMismatchStep.TypedDictKey(codifiedType(context, expected), mismatch.getKey(), mismatch.getProblem(), actualValue,
                                               expectedValue);
    }

    /**
     * The type span for the contravariant line, pre-rendered so the sentence's words stay outside the code block:
     * a protocol reads "protocol `X`" (only `X` in a code span, keeping its link), everything else reads "`X`". The
     * "protocol" marker matters because otherwise two differently-named protocols look like unrelated,
     * arbitrarily-incompatible names. The result already carries its own {@code <code>} span, so the contravariant
     * templates embed it without backticks.
     */
    private static CodifiedParam codifiedContravariantType(MatchContext context, @Nullable PyType type) {
        CodifiedParam base = codifiedType(context, type);
        ProblemMessage wrapped = type instanceof PyClassLikeType classLikeType && isProtocol(classLikeType, context.context)
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerProseProtocolType(params[0]), base)
            : PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerProsePlainType(params[0]), base);
        return new CodifiedParam(wrapped.description(), PyInspectionMessages.tooltipFragment(wrapped));
    }

    private static ProblemMessage typeNotAssignable(MatchContext context, @Nullable PyType actual, @Nullable PyType expected) {
        return PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerTypeNotAssignable(params[0], params[1]),
                                                   codifiedType(context, actual), codifiedType(context, expected));
    }

    /**
     * Records a single "not assignable" leaf for a failed overload match (when {@code matched} is {@code false}) and
     * returns {@code matched} unchanged, so the overload branches stay one-liners.
     */
    private static boolean recordOverloadLeaf(MatchContext context, @Nullable PyType expected, @Nullable PyType actual, boolean matched) {
        if (!matched) {
            recordLeaf(context, () -> new PyMismatchStep.Nominal(codifiedType(context, actual), codifiedType(context, expected)),
                       () -> typeNotAssignable(context, actual, expected));
        }
        return matched;
    }

    /**
     * Best-effort reason a protocol member didn't match, used as the breakdown node above the type leaf.
     */
    private static ProblemMessage protocolMemberFailureMessage(PyTypeMember protocolMember,
                                                               List<PyTypeMember> candidates,
                                                               TypeEvalContext context) {
        String name = protocolMember.getName();
        PyTypeMember candidate = candidates.size() == 1 ? candidates.get(0) : null;
        if (candidate != null) {
            if (PyTypeUtil.isSettable(protocolMember, context) && !PyTypeUtil.isSettable(candidate, context)) {
                return PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownMemberNotWritable(params[0]), name);
            }
            if (protocolMember.isClassVar() != candidate.isClassVar()) {
                return PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownMemberClassvarMismatch(params[0]), name);
            }
        }
        return PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownMemberIncompatible(params[0]), name);
    }

    private static Optional<Boolean> match(@Nullable PyType expected, @Nullable PyType actual, MatchContext context) {
        PyCodeInsightCounters.inc(Counter.MATCH_STEPS);
        PyAnyType.validate(expected);
        PyAnyType.validate(actual);
        Optional<Boolean> result = RecursionManager.doPreventingRecursion(Pair.create(expected, actual), false, () ->
            matchImpl(expected, actual, context)
        );

        return result != null ? result : Optional.of(true);
    }

    /**
     * Perform type matching.
     * <p>
     * <p>
     * Implementation details:
     * <ul>
     *  <li>The method mutates {@code context.substitutions} map adding new entries into it</li>
     *  <li>The order of match subroutine calls is important</li>
     *  <li>The method may recursively call itself</li>
     * </ul>
     */
    private static Optional<Boolean> matchImpl(@Nullable PyType expected, @Nullable PyType actual, MatchContext context) {
        if (Objects.equals(expected, actual)) {
            return Optional.of(true);
        }

        Optional<Boolean> extensionResult = PyTypeCheckerExtension.EP_NAME.computeSafeIfAny(it -> {
            Optional<Boolean> result = it.match(expected, actual, context.context, context.mySubstitutions);
            return result.isPresent() ? result : null;
        });
        if (extensionResult != null) {
            return extensionResult;
        }

        if (expected instanceof PyTypeFormType typeFormType) {
            return Optional.of(match(typeFormType, actual, context));
        }

        if (expected instanceof PyClassType classType) {
            Optional<Boolean> match = matchObject(classType, actual);
            if (match.isPresent()) {
                return match;
            }
        }

        // `PyTopType` is the top type `object`: every type is assignable to it, just like `matchObject` accepts
        // any `actual` when `expected` is the `object` class.
        if (expected instanceof PyTopType) {
            return Optional.of(true);
        }

        if (actual instanceof PyNarrowedType actualNarrowed && expected instanceof PyNarrowedType expectedNarrowed) {
            return match(expectedNarrowed, actualNarrowed, context);
        }

        if (actual instanceof PyTypeVarTupleType actualTypeVarTuple && context.reversedSubstitutions) {
            return Optional.of(match((PyPositionalVariadicType) actualTypeVarTuple, expected, context));
        }

        if (expected instanceof PyPositionalVariadicType positionalVariadicType) {
            return Optional.of(match(positionalVariadicType, actual, context));
        }

        if (actual instanceof PyTypeVarType actualTypeVar && context.reversedSubstitutions) {
            return Optional.of(match(actualTypeVar, expected, context));
        }

        if (expected instanceof PyTypeVarType typeVarType) {
            return Optional.of(match(typeVarType, actual, context));
        }

        if (expected instanceof PySelfType selfType) {
            return Optional.of(match(selfType, actual, context));
        }

        if (actual instanceof PySelfType actualSelfType && context.reversedSubstitutions) {
            return Optional.of(match(actualSelfType, expected, context));
        }

        if (actual instanceof PyParamSpecType actualParamSpec && context.reversedSubstitutions) {
            return Optional.of(match(actualParamSpec, expected, context));
        }

        if (expected instanceof PyParamSpecType paramSpecType) {
            return Optional.of(match(paramSpecType, actual, context));
        }

        if (expected instanceof PyConcatenateType concatenateType) {
            return Optional.of(match(concatenateType, actual, context));
        }

        if (isAnyOrUnknown(expected) || containsAny(actual, context.context)) {
            return Optional.of(true);
        }

        if (expected instanceof PyCallableParameterListType parameterListType) {
            return Optional.of(match(parameterListType, actual, context));
        }

        if (actual instanceof PyNeverType) {
            return Optional.of(true);
        }

        if (expected instanceof PyNeverType) {
            return Optional.of(false);
        }

        // Composite types (PY-91327). The six branches below look alike but differ per branch:
        //
        //   branch                         quantifier   breakdown
        //   actual is PyUnionType          strict flag  per failing member
        //   expected is PyUnionType        any          one summary frame
        //   actual is PyUnsafeUnionType    any          none
        //   expected is PyUnsafeUnionType  any          one summary frame
        //   expected is PyIntersectionType all          per failing member
        //   actual is PyIntersectionType   any          one summary frame
        //
        // Unions match actual-first, intersections expected-first, because the `all` quantifier must be the outer
        // check for a composite-vs-composite match to distribute. It sits on the actual side of a union and the
        // expected side of an intersection, so `A & B <: C & D` unfolds as
        // `(A <: C or B <: C) and (A <: D or B <: D)`.
        //
        // Both sides are walked, so composite-vs-composite costs |actual| x |expected|; [maxBreakdownMembers]
        // caps the per-member detail.

        if (actual instanceof PyUnionType actualUnion) {
            return Optional.of(match(expected, actualUnion, context));
        }

        if (expected instanceof PyUnionType expectedUnion) {
            return Optional.of(match(expectedUnion, actual, context));
        }

        if (actual instanceof PyUnsafeUnionType actualUnsafeUnion) {
            return Optional.of(match(expected, actualUnsafeUnion, context));
        }

        if (expected instanceof PyUnsafeUnionType expectedUnsafeUnion) {
            return Optional.of(match(expectedUnsafeUnion, actual, context));
        }

        if (expected instanceof PyIntersectionType expectedIntersection) {
            return Optional.of(match(expectedIntersection, actual, context));
        }

        if (actual instanceof PyIntersectionType actualIntersection) {
            return Optional.of(match(expected, actualIntersection, context));
        }

        if (actual instanceof PyCustomType customType) {
            return Optional.of(match(expected, customType, context));
        }

        if (expected instanceof PyClassType expectedClass && actual instanceof PyClassType actualClass) {
            DiagnosticsCollector collector = context.diagnostics;
            int reasonsBefore = collector != null ? collector.current.size() : 0;
            Optional<Boolean> match = match(expectedClass, actualClass, context);
            if (match.isPresent()) {
                // Several class-vs-class sub-branches (e.g. an outright incompatible pair like `bytes`/`int`)
                // decide `false` without recording a reason, unlike the protocol/TypedDict/type-argument paths.
                // Add the universal leaf only when nothing was recorded, so breakdowns never lose a member.
                if (collector != null && !match.get() && collector.current.size() == reasonsBefore) {
                    recordLeaf(context, () -> new PyMismatchStep.Nominal(codifiedType(context, actual), codifiedType(context, expected)),
                               () -> typeNotAssignable(context, actual, expected));
                }
                return match;
            }
        }

        if (actual instanceof PyStructuralType structuralType && structuralType.isInferredFromUsages()) {
            return Optional.of(true);
        }

        if (expected instanceof PyStructuralType expectedStructural) {
            return Optional.of(match(expectedStructural, actual, context.context));
        }

        if (actual instanceof PyStructuralType actualStructural && expected instanceof PyClassType expectedClass) {
            Set<String> expectedAttributes = expectedClass.getMemberNames(true, context.context);
            return Optional.of(expectedAttributes.containsAll(actualStructural.getAttributeNames()));
        }

        if (actual instanceof PyCallableType actualCallable && expected instanceof PyCallableType expectedCallable) {
            Optional<Boolean> match = actualCallable instanceof PyClassType actualClass && actualClass.isDefinition() &&
                !(expectedCallable instanceof PyClassLikeType)
                ? matchClassAsCallable(expectedCallable, actualCallable, context)
                : match(expectedCallable, actualCallable, context);
            if (match.isPresent()) {
                return match;
            }
        }

        if (expected instanceof PyModuleType expectedModule) {
            return Optional.of(actual instanceof PyModuleType actualModule && expectedModule.getModule() == actualModule.getModule());
        }

        if (expected instanceof PyClassType expectedClass && actual instanceof PyModuleType actualModule) {
            if (isProtocol(expectedClass, context.context)) {
                return Optional.of(match(expectedClass, actualModule, context));
            }
            PyClassType moduleClassType = actualModule.getModuleClassType();
            return match(expected, moduleClassType != null ? moduleClassType : PyAnyType.getUnknown(), context);
        }

        // Handle PyOverloadType matching. Each branch fans out over candidate overloads; we discard the
        // per-candidate dead-ends ([withoutRecording]) and record a single summarizing leaf instead.
        if (expected instanceof PyOverloadType expectedOverload) {
            if (actual instanceof PyOverloadType actualOverload) {
                // When both are overload types, check if all overloads in expected have a match in actual (subset matching)
                boolean matched = withoutRecording(context, () ->
                    expectedOverload.getItems().stream().allMatch(expectedItem ->
                        actualOverload.getItems().stream().anyMatch(actualItem ->
                            match(expectedItem, actualItem, context).orElse(false)
                        )
                    )
                );
                return Optional.of(recordOverloadLeaf(context, expected, actual, matched));
            }
            // If expected is overload but actual is not, check if actual is a callable class/protocol
            // Extract the __call__ type and compare with the overload
            if (actual instanceof PyClassLikeType actualClassLike && actualClassLike.isCallable()) {
                boolean matched = withoutRecording(context, () -> matchOverloadWithCallable(expectedOverload, actualClassLike, context, true));
                return Optional.of(recordOverloadLeaf(context, expected, actual, matched));
            }
            return Optional.of(recordOverloadLeaf(context, expected, actual, false));
        }

        if (actual instanceof PyOverloadType actualOverload) {
            // If actual is overload but expected is not, first check if expected is a callable protocol
            if (expected instanceof PyClassLikeType expectedClassLike && expectedClassLike.isCallable()) {
                boolean matched = withoutRecording(context, () -> matchOverloadWithCallable(actualOverload, expectedClassLike, context, false));
                return Optional.of(recordOverloadLeaf(context, expected, actual, matched));
            }
            // Otherwise, check if any overload in actual matches expected.
            // An overload is tried on a copy first, so that a failed overload does not bind type parameters for the next one.
            boolean matched = withoutRecording(context, () ->
                actualOverload.getItems().stream().anyMatch(item ->
                    matchOnCopy(context, it -> match(expected, item, it)).equals(Optional.of(true))
                )
            );
            return Optional.of(recordOverloadLeaf(context, expected, actual, matched));
        }

        boolean numericMatch = !PyNumericTowerUtil.isEnabled() && matchNumericTypes(expected, actual);
        if (!numericMatch) {
            // Universal terminal reason: every plain class / numeric mismatch funnels through here.
            recordLeaf(context, () -> new PyMismatchStep.Nominal(codifiedType(context, actual), codifiedType(context, expected)),
                       () -> typeNotAssignable(context, actual, expected));
        }
        return Optional.of(numericMatch);
    }

    private static Optional<Boolean> match(PyNarrowedType expectedNarrowedType,
                                           PyNarrowedType actualNarrowedType,
                                           MatchContext context) {
        if (expectedNarrowedType.getTypeIs() != actualNarrowedType.getTypeIs()) {
            return Optional.of(false);
        }
        if (expectedNarrowedType.getTypeIs()) {
            return Optional.of(Objects.equals(expectedNarrowedType.getNarrowedType(), actualNarrowedType.getNarrowedType()));
        }
        return match(expectedNarrowedType.getNarrowedType(), actualNarrowedType.getNarrowedType(), context);
    }

    private static boolean match(PyTypeFormType expected, @Nullable PyType actual, MatchContext context) {
        if (actual == null) {
            return true;
        }
        PyType representedActual = PyTypeFormType.representedTypeOf(actual);
        if (representedActual == null) {
            // `actual` does not denote a type expression; only an unknown type may still match.
            return isAnyOrUnknown(actual) || containsAny(actual, context.context);
        }
        return match(expected.getRepresentedType(), representedActual, context).orElse(true);
    }

    /**
     * Check whether {@code expected} is Python <i>object</i> or <i>type</i>.
     * <p>
     * <p>
     * {@see PyTypeChecker#match(PyType, PyType, TypeEvalContext, Map)}
     */
    private static Optional<Boolean> matchObject(PyClassType expected, @Nullable PyType actual) {
        if (ArrayUtil.contains(expected.getName(), PyNames.OBJECT, PyNames.TYPE)) {
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(expected.getPyClass());
            if (expected.equals(builtinCache.getObjectType())) {
                return Optional.of(true);
            }
            if (expected.equals(builtinCache.getTypeType()) &&
                actual instanceof PyInstantiableType<?> instantiableType && instantiableType.isDefinition()) {
                return Optional.of(true);
            }
        }
        return Optional.empty();
    }

    /**
     * Match {@code actual} versus {@link PyTypeVarType} expected.
     * <p>
     * <p>
     * The method mutates {@code context.substitutions} map adding new entries into it
     */
    private static boolean match(PyTypeVarType expected, @Nullable PyType actual, MatchContext context) {
        if (expected.isDefinition() && actual instanceof PyInstantiableType<?> instantiableType && !instantiableType.isDefinition()) {
            return false;
        }

        Ref<@Nullable PyType> substitutedRef = context.mySubstitutions.getTypeVars().get(expected);
        Ref<? extends PyType> defaultTypeRef = expected.getDefaultType();
        if (defaultTypeRef != null) {
            PyType defaultType = defaultTypeRef.get();
            // Skip default substitution
            // TODO consulo: Ref.deref missing
            if (defaultType != null && defaultType.equals(substitutedRef != null ? substitutedRef.get() : null)) {
                substitutedRef = null;
            }
        }
        PyType bound = expected.getBound() != null ? expected.getBound() : PyAnyType.getUnknown();
        List<@Nullable PyType> constraints = expected.getConstraints();
        // Promote int in Type[TypeVar('T', int)] to Type[int] before checking that bounds match
        if (expected.isDefinition()) {
            bound = convertToClass(bound);
            constraints = ContainerUtil.map(constraints, PyTypeChecker::convertToClass);
        }

        // Remove value-specific components from the actual type to make it safe to propagate
        PyType safeActual = constraints.isEmpty() && bound instanceof PyLiteralStringType ? actual : replaceLiteralStringWithStr(actual, context.context);
        safeActual = PyNumericTowerUtil.enrich(safeActual);

        if (substitutedRef != null) {
            PyType substitution = substitutedRef.get();
            if (expected.equals(safeActual) || expected.equals(substitution)) {
                return true;
            }

            MyMatchHelper matchHelper = new MyMatchHelper(expected, context);
            if (matchHelper.match(substitution, safeActual)) {
                return true;
            }
            if (context.mySubstitutions.getFrozenTypeVars(KeyImpl.INSTANCE).contains(expected)) {
                return false;
            }
            if (!matchHelper.match(safeActual, substitution)) {
                safeActual = PyUnionType.unionOrUnknown(safeActual, substitution);
            }
        }

        int matchedConstraintIndex = -1;
        if (constraints.isEmpty()) {
            Optional<Boolean> match = match(bound, safeActual, context);
            if (match.isPresent() && !match.get()) {
                return false;
            }
        }
        else {
            PyType finalSafeActual = safeActual;
            for (int i = 0; i < constraints.size(); i++) {
                if (match(constraints.get(i), finalSafeActual, context).orElse(true)) {
                    matchedConstraintIndex = i;
                    break;
                }
            }
            if (matchedConstraintIndex == -1) {
                return false;
            }
        }

        if (!PyTypeUtilKt.isUnknown(safeActual)) {
            PyType type;
            if (constraints.isEmpty()) {
                // temporary special casing to avoid Literal problems PY-90366
                type = PyTypeInferenceCspSolverKt.isWidenBoundsCase(expected, context.context) ? PyLiteralType.upcastLiteralToClass(safeActual) : safeActual;
            }
            else {
                type = constraints.get(matchedConstraintIndex);
            }
            context.mySubstitutions.putTypeVar(expected, Ref.create(type), KeyImpl.INSTANCE);
        }
        else {
            PyType effectiveBound = expected.getEffectiveBound();
            if (!PyTypeUtilKt.isUnknown(effectiveBound)) {
                context.mySubstitutions.putTypeVar(expected, Ref.create(PyUnionType.createWeakType(effectiveBound)), KeyImpl.INSTANCE);
            }
        }

        return true;
    }

    private static boolean match(PySelfType expected, @Nullable PyType actual, MatchContext context) {
        if (isAnyOrUnknown(actual)) {
            return true;
        }
        PyInstantiableType<?> selfType = context.mySubstitutions.getSelfType();
        if (selfType != null && !(selfType instanceof PySelfType)) {
            PyType substitution = expected.isDefinition() ? selfType.toClass() : selfType.toInstance();
            return match(substitution, actual, context).orElse(false);
        }
        if (!(actual instanceof PySelfType actualSelf)) {
            // A final class has no subclass, so `Self` in it denotes the class itself. A class with own type parameters
            // is excluded: `Self` then denotes the class with those parameters, which the bare scope type cannot express.
            return PyTypingTypeProvider.isFinalClass(expected.getPyClass(), context.context) &&
                !hasOwnTypeParameters(expected.getPyClass(), context.context) &&
                match(expected.getScopeClassType(), actual, context).orElse(false);
        }
        return expected.isDefinition() == actualSelf.isDefinition() &&
            match(expected.getScopeClassType(), actualSelf.getScopeClassType(), context).orElse(false);
    }

    /**
     * Returns true if and only if {@code cls} declares its own type parameters. A class that only specializes
     * a generic ancestor, for example {@code class C(list[int])}, declares none.
     */
    private static boolean hasOwnTypeParameters(PyClass cls, TypeEvalContext context) {
        return PyTypeProvider.EP_NAME.findFirstSafe(it -> it.getGenericType(cls, context) != null) != null;
    }

    private static @Nullable PyType convertToClass(@Nullable PyType type) {
        return PyTypeUtil.toStream(type)
            .map(it -> it instanceof PyInstantiableType<?> instantiableType ? (PyType) instantiableType.toClass() : it)
            .collect(PyTypeUtil.toUnion(type));
    }

    private static boolean match(PyPositionalVariadicType expected, @Nullable PyType actual, MatchContext context) {
        if (PyTypeUtilKt.isUnknown(actual)) {
            return true;
        }
        if (!(actual instanceof PyPositionalVariadicType actualVariadic)) {
            return false;
        }
        if (expected instanceof PyUnpackedTupleType expectedUnpacked) {
            // The actual type is just a TypeVarTuple
            if (!(actualVariadic instanceof PyUnpackedTupleType actualUnpacked)) {
                return false;
            }
            if (expectedUnpacked.isUnbound()) {
                PyType repeatedExpectedType = expectedUnpacked.getElementTypes().get(0);
                if (actualUnpacked.isUnbound()) {
                    return match(repeatedExpectedType, actualUnpacked.getElementTypes().get(0), context).orElse(false);
                }
                else {
                    return actualUnpacked.getElementTypes().stream()
                        .allMatch(singleActualType -> match(repeatedExpectedType, singleActualType, context).orElse(false));
                }
            }
            else {
                if (actualUnpacked.isUnbound()) {
                    PyType repeatedActualType = actualUnpacked.getElementTypes().get(0);
                    return expectedUnpacked.getElementTypes().stream()
                        .allMatch(singleExpectedType -> match(singleExpectedType, repeatedActualType, context).orElse(false));
                }
                else {
                    return matchTypeParameters(null, expectedUnpacked.getElementTypes(), actualUnpacked.getElementTypes(), context);
                }
            }
        }
        else {
            PyPositionalVariadicType substitution = context.mySubstitutions.getTypeVarTuples().get(expected);
            PyPositionalVariadicType safeActual = enrichVariadicType(actualVariadic);
            if (substitution != null && !substitution.equals(PyUnpackedTupleTypeImpl.UNSPECIFIED)) {
                if (expected.equals(safeActual) || substitution.equals(expected)) {
                    return true;
                }
                return context.reversedSubstitutions
                    ? match(safeActual, (PyType) substitution, context)
                    : match(substitution, (PyType) safeActual, context);
            }
            if (expected instanceof PyTypeVarTupleType expectedTypeVarTuple && safeActual instanceof PyUnpackedTupleType safeUnpacked) {
                PyType bound = expectedTypeVarTuple.getBound();
                boolean match;
                if (bound instanceof PyUnpackedTupleType boundUnpacked) {
                    match = match((PyPositionalVariadicType) boundUnpacked, actual, context);
                }
                else {
                    match = safeUnpacked.getElementTypes().stream().allMatch(it -> match(bound, it, context).get());
                }
                if (!match) {
                    return false;
                }
            }
            PyPositionalVariadicType normalizedActual;
            if (safeActual instanceof PyUnpackedTupleType safeUnpacked) {
                // TODO: consider how widening should work with more complex types like: `tuple[Sequence[Literal[1]]`
                normalizedActual = new PyUnpackedTupleTypeImpl(ContainerUtil.map(safeUnpacked.getElementTypes(), PyLiteralType::upcastLiteralToClass),
                                                               safeUnpacked.isUnbound());
            }
            else {
                normalizedActual = safeActual;
            }
            context.mySubstitutions.putTypeVarTuple((PyTypeVarTupleType) expected, normalizedActual, KeyImpl.INSTANCE);
        }
        return true;
    }

    private static PyPositionalVariadicType enrichVariadicType(PyPositionalVariadicType variadic) {
        if (variadic instanceof PyUnpackedTupleType unpackedTupleType) {
            List<@Nullable PyType> enrichedElements = ContainerUtil.map(unpackedTupleType.getElementTypes(), PyNumericTowerUtil::enrich);
            return new PyUnpackedTupleTypeImpl(enrichedElements, unpackedTupleType.isUnbound());
        }
        return variadic;
    }

    private static @Nullable PyType replaceLiteralStringWithStr(@Nullable PyType actual, TypeEvalContext context) {
        return PyCloningTypeVisitor.clone(actual, new PyCloningTypeVisitor(context) {
            @Override
            public PyType visitPyLiteralStringType(PyLiteralStringType literalStringType) {
                return new PyClassTypeImpl(literalStringType.getPyClass(), false);
            }
        });
    }

    private static boolean match(PyParamSpecType expected, @Nullable PyType actual, MatchContext context) {
        if (isAnyOrUnknown(actual)) {
            return true;
        }
        if (!(actual instanceof PyCallableParameterVariadicType actualVariadic)) {
            return false;
        }

        PyType bound = expected.getBound();

        // Remove value-specific components from the actual type to make it safe to propagate
        PyType safeActual = bound instanceof PyLiteralStringType ? actual : replaceLiteralStringWithStr(actual, context.context);
        safeActual = PyNumericTowerUtil.enrich(safeActual);

        Optional<Boolean> match = match(bound, safeActual, context);
        if (match.isPresent() && !match.get()) {
            return false;
        }

        context.mySubstitutions.putParamSpec(expected, actualVariadic, KeyImpl.INSTANCE);
        return true;
    }

    private static boolean match(PyConcatenateType expected, @Nullable PyType actual, MatchContext context) {
        if (actual == null) {
            return true;
        }
        List<@Nullable PyType> expectedFirstTypes = expected.getFirstTypes();
        int expectedPrefixSize = expectedFirstTypes.size();
        PyParamSpecType expectedParamSpec = expected.getParamSpec();
        if (actual instanceof PyConcatenateType actualConcatenate) {
            if (expectedPrefixSize > actualConcatenate.getFirstTypes().size()) {
                return false;
            }
            List<@Nullable PyType> actualFirstTypes = actualConcatenate.getFirstTypes().subList(0, expectedPrefixSize);
            if (!match(expectedFirstTypes, actualFirstTypes, context)) {
                return false;
            }
            if (expectedParamSpec == null) {
                return true;
            }
            if (actualFirstTypes.size() > expectedPrefixSize) {
                return match(
                    expectedParamSpec,
                    new PyConcatenateType(
                        expectedFirstTypes.subList(0, actualFirstTypes.size()),
                        actualConcatenate.getParamSpec()
                    ), context
                );
            }
            else {
                return match(expectedParamSpec, actualConcatenate.getParamSpec(), context);
            }
        }
        else if (actual instanceof PyCallableParameterListType actualParameterList) {
            if (expectedPrefixSize > actualParameterList.getParameters().size()) {
                return false;
            }
            List<@Nullable PyType> actualFirstParamTypes =
                ContainerUtil.map(actualParameterList.getParameters().subList(0, expectedPrefixSize), it -> it.getType(context.context));
            if (!match(expectedFirstTypes, actualFirstParamTypes, context)) {
                return false;
            }
            if (expectedParamSpec == null) {
                return true;
            }
            return match(
                expectedParamSpec,
                new PyCallableParameterListTypeImpl(ContainerUtil.subList(actualParameterList.getParameters(), expectedPrefixSize)),
                context
            );
        }
        return false;
    }

    private static boolean match(PyCallableParameterListType expectedParameters,
                                 @Nullable PyType actual,
                                 MatchContext context) {
        if (actual == null) {
            return true;
        }
        if (!(actual instanceof PyCallableParameterListType actualParameters)) {
            return false;
        }
        return match(expectedParameters, actualParameters, context);
    }

    /**
     * Whether {@code composite} is too wide for a per-member breakdown. See {@link #maxBreakdownMembers()}.
     */
    private static boolean exceedsBreakdownBound(PyCompositeTypeBase composite) {
        int bound = maxBreakdownMembers();
        return bound > 0 && composite.getMembers().size() > bound;
    }

    private static boolean match(@Nullable PyType expected, PyUnionType actual, MatchContext context) {
        if (expected instanceof PyTupleType expectedTuple) {
            // XXX A type-widening hack for cases like PyTypeTest.testDictFromTuple
            Optional<Boolean> match = match(expectedTuple, actual, context);
            if (match.isPresent()) {
                return match.get();
            }
        }

        // `||` short-circuits, so when strict semantics are off the literal scan still runs exactly as before.
        boolean requireAll = PyUnionType.isStrictSemanticsEnabled() || // checking strictly separately until PY-24834 gets implemented
            actual.getMembers().stream().anyMatch(it -> it instanceof PyLiteralStringType || it instanceof PyLiteralType);
        // Short-circuit when no breakdown is collected, and when the union is too wide to break down.
        if (context.diagnostics == null || exceedsBreakdownBound(actual)) {
            boolean shortCircuited = withoutDiagnostics(context, () ->
                requireAll
                    ? actual.getMembers().stream().allMatch(it -> match(expected, it, context).orElse(false))
                    : actual.getMembers().stream().anyMatch(it -> match(expected, it, context).orElse(false))
            );
            if (!shortCircuited) {
                recordLeaf(context,
                           () -> new PyMismatchStep.NoUnionMember(codifiedType(context, actual), codifiedType(context, expected), true),
                           () -> PyInspectionMessages.problemMessage(
                               params -> PyLocalize.inspTypeCheckerBreakdownUnionMemberNotAssignable(params[0], params[1]),
                               codifiedType(context, actual), codifiedType(context, expected)));
            }
            return shortCircuited;
        }

        // One walk: buffer each failing member's reasons ([collectInto]), then pick the shape below. The shape
        // depends on how many members fail, which is only known at the end.
        DiagnosticsCollector collector = Objects.requireNonNull(context.diagnostics);
        List<Pair<@Nullable PyType, List<PyTypeMismatchExplanation>>> failing = new ArrayList<>();
        for (PyType member : actual.getMembers()) {
            Pair<Boolean, List<PyTypeMismatchExplanation>> collected =
                collectInto(collector, () -> match(expected, member, context).orElse(false));
            if (!collected.getFirst()) {
                failing.add(Pair.create(member, collected.getSecond()));
            }
        }
        boolean matched = requireAll ? failing.isEmpty() : failing.size() < actual.getMembers().size();
        if (matched) {
            return true;
        }

        if (failing.size() == 1) {
            // Exactly one incompatible member: the "Not all members …" umbrella and a naming frame would only add
            // redundant levels (the member's own reason already names it, e.g. "`C` is incompatible with protocol `P`"),
            // so record that reason directly under the headline, which already shows the whole union.
            collector.current.addAll(failing.get(0).getSecond());
        }
        else {
            // Several incompatible members: the "Not all members of X are assignable to Y" umbrella, then a frame per
            // failing member that NAMES it (carrying a UnionMember step), so the several otherwise-indistinguishable
            // structural reasons can be told apart. The prose renderer FOLDS a member whose reason self-names (a missing
            // or wrong-typed protocol attribute) into one arm line and drops this wrapper, keeping the wrapper line above
            // the reason only when it doesn't (e.g. two callables differing by a parameter name).
            recordFrameBool(context, () ->
                PyInspectionMessages.problemMessage(
                    params -> PyLocalize.inspTypeCheckerBreakdownUnionMemberNotAssignable(params[0], params[1]),
                    codifiedType(context, actual), codifiedType(context, expected)
                ), () ->
                // The PROVIDED value is the union here (`expected` is the single required type each member failed against),
                // so mark the direction — a call-site renderer must not read the required type as a "union with members".
                new PyMismatchStep.NoUnionMember(codifiedType(context, actual), codifiedType(context, expected), true), () -> {
                for (Pair<@Nullable PyType, List<PyTypeMismatchExplanation>> pair : failing) {
                    PyType member = pair.getFirst();
                    ProblemMessage message = PyInspectionMessages.problemMessage(
                        params -> PyLocalize.inspTypeCheckerBreakdownUnionMemberIncompatible(params[0], params[1]),
                        codifiedType(context, member), codifiedType(context, expected));
                    collector.current.add(new PyTypeMismatchExplanation(message, pair.getSecond(),
                                                                        new PyMismatchStep.UnionMember(codifiedType(context, member))));
                }
                return false;
            });
        }
        return false;
    }

    private static Optional<Boolean> match(PyTupleType expected,
                                           PyUnionType actual,
                                           MatchContext context) {
        int elementCount = expected.getElementCount();

        if (!expected.isHomogeneous()) {
            PyTupleType widenedActual = widenUnionOfTuplesToTupleOfUnions(actual, elementCount);
            if (widenedActual != null) {
                return match(expected, widenedActual, context);
            }
        }

        return Optional.empty();
    }

    private static boolean match(PyUnionType expected, @Nullable PyType actual, MatchContext context) {
        if (expected.getMembers().contains(actual)) {
            return true;
        }
        // When collecting a breakdown, record why `actual` fails against every member under one summary
        // node (each member's own match contributes its sub-reasons); otherwise this collapses to the
        // original `any` with zero overhead.
        return recordFrameBool(context, () ->
            PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerBreakdownNotAssignableToUnion(params[0], params[1]),
                codifiedType(context, actual), codifiedType(context, expected)
            ), () ->
            new PyMismatchStep.NoUnionMember(codifiedType(context, actual), codifiedType(context, expected)), () -> {
            // Past the bound the summary node keeps its line but loses the per-member sub-reasons.
            if (exceedsBreakdownBound(expected)) {
                return withoutDiagnostics(context, () -> expected.getMembers().stream().anyMatch(type -> match(type, actual, context).orElse(true)));
            }
            else {
                return expected.getMembers().stream().anyMatch(type -> match(type, actual, context).orElse(true));
            }
        });
    }

    /**
     * The provided value is the intersection, so the quantifier is {@code any}: a value that is both {@code A} and {@code B} is
     * accepted wherever either member is. Mirrors the expected-side union case.
     */
    private static boolean match(@Nullable PyType expected, PyIntersectionType actual, MatchContext context) {
        return recordFrameBool(context, () ->
            PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerBreakdownIntersectionMemberNotAssignable(params[0], params[1]),
                codifiedType(context, actual), codifiedType(context, expected)
            ), () ->
            new PyMismatchStep.NoIntersectionMember(codifiedType(context, expected), codifiedType(context, actual)), () -> {
            if (exceedsBreakdownBound(actual)) {
                return withoutDiagnostics(context, () -> actual.getMembers().stream().anyMatch(type -> match(expected, type, context).orElse(false)));
            }
            else {
                return actual.getMembers().stream().anyMatch(type -> match(expected, type, context).orElse(false));
            }
        });
    }

    /**
     * The required side is the intersection, so the quantifier is {@code all}: every member is an independent
     * requirement, and each failing one earns its own frame.
     */
    private static boolean match(PyIntersectionType expected, @Nullable PyType actual, MatchContext context) {
        DiagnosticsCollector collector = context.diagnostics;
        if (collector == null || exceedsBreakdownBound(expected)) {
            boolean shortCircuited = withoutDiagnostics(context, () ->
                expected.getMembers().stream().allMatch(type -> match(type, actual, context).orElse(true))
            );
            if (!shortCircuited) {
                recordLeaf(context,
                           () -> new PyMismatchStep.NoIntersectionMember(codifiedType(context, actual), codifiedType(context, expected), true),
                           () -> PyInspectionMessages.problemMessage(
                               params -> PyLocalize.inspTypeCheckerBreakdownNotAssignableToIntersection(params[0], params[1]),
                               codifiedType(context, actual), codifiedType(context, expected)));
            }
            return shortCircuited;
        }

        // One walk, one buffer per failing member, as the actual-side union does.
        List<Pair<@Nullable PyType, List<PyTypeMismatchExplanation>>> failing = new ArrayList<>();
        for (PyType member : expected.getMembers()) {
            Pair<Boolean, List<PyTypeMismatchExplanation>> collected =
                collectInto(collector, () -> match(member, actual, context).orElse(true));
            if (!collected.getFirst()) {
                failing.add(Pair.create(member, collected.getSecond()));
            }
        }
        if (failing.isEmpty()) {
            return true;
        }

        if (failing.size() == 1) {
            // One unmet requirement: its own reason already names the member, so an umbrella would only add a level.
            collector.current.addAll(failing.get(0).getSecond());
        }
        else {
            recordFrameBool(context, () ->
                PyInspectionMessages.problemMessage(
                    params -> PyLocalize.inspTypeCheckerBreakdownNotAssignableToIntersection(params[0], params[1]),
                    codifiedType(context, actual), codifiedType(context, expected)
                ), () ->
                new PyMismatchStep.NoIntersectionMember(codifiedType(context, actual), codifiedType(context, expected), true), () -> {
                for (Pair<@Nullable PyType, List<PyTypeMismatchExplanation>> pair : failing) {
                    PyType member = pair.getFirst();
                    ProblemMessage message = PyInspectionMessages.problemMessage(
                        params -> PyLocalize.inspTypeCheckerBreakdownIntersectionMemberIncompatible(params[0], params[1]),
                        codifiedType(context, actual), codifiedType(context, member));
                    collector.current.add(new PyTypeMismatchExplanation(message, pair.getSecond(),
                                                                        new PyMismatchStep.IntersectionMember(codifiedType(context, member))));
                }
                return false;
            });
        }
        return false;
    }

    private static boolean match(@Nullable PyType expected, PyUnsafeUnionType actual, MatchContext context) {
        return actual.getMembers().stream().anyMatch(type -> match(expected, type, context).orElse(false));
    }

    private static boolean match(PyUnsafeUnionType expected, @Nullable PyType actual, MatchContext context) {
        if (expected.getMembers().contains(actual)) {
            return true;
        }
        // Mirror of the [PyUnionType] case: record a per-member breakdown when collecting, otherwise plain `any`,
        // and drop the sub-reasons past the breakdown bound.
        return recordFrameBool(context, () ->
            PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerBreakdownNotAssignableToUnion(params[0], params[1]),
                codifiedType(context, actual), codifiedType(context, expected)
            ), () ->
            new PyMismatchStep.NoUnionMember(codifiedType(context, actual), codifiedType(context, expected)), () -> {
            if (exceedsBreakdownBound(expected)) {
                return withoutDiagnostics(context, () -> expected.getMembers().stream().anyMatch(type -> match(type, actual, context).orElse(true)));
            }
            else {
                return expected.getMembers().stream().anyMatch(type -> match(type, actual, context).orElse(true));
            }
        });
    }

    // PyCustomType operates as an implicit intersection of its "typesToMimic"
    private static boolean match(@Nullable PyType expected, PyCustomType actual, MatchContext context) {
        return actual.getTypesToMimic().stream().anyMatch(type -> match(expected, type, context).orElse(false));
    }

    private static Optional<Boolean> match(PyClassType expected,
                                           PyClassType actual,
                                           MatchContext matchContext) {
        if (expected.equals(actual)) {
            return Optional.of(true);
        }

        TypeEvalContext context = matchContext.context;

        if (expected.isDefinition() ^ actual.isDefinition() && !isProtocol(expected, context)) {
            if (!expected.isDefinition() && actual.isDefinition()) {
                PyClassLikeType metaClass = actual.getMetaClassType(context, true);
                return Optional.of(
                    metaClass != null && match((PyType) expected, (PyType) metaClass.toInstance(), matchContext).orElse(true)
                );
            }
            return Optional.of(false);
        }

        if (expected instanceof PyTupleType expectedTuple && actual instanceof PyTupleType actualTuple) {
            return match(expectedTuple, actualTuple, matchContext);
        }

        if (expected instanceof PyLiteralType expectedLiteral) {
            return Optional.of(actual instanceof PyLiteralType actualLiteral && PyLiteralType.match(expectedLiteral, actualLiteral));
        }

        if (expected instanceof PyTypedDictType && !(actual instanceof PyTypedDictType)) {
            recordLeaf(matchContext,
                       () -> new PyMismatchStep.Nominal(codifiedType(matchContext, actual), codifiedType(matchContext, expected)),
                       () -> PyInspectionMessages.problemMessage(
                           params -> PyLocalize.inspTypeCheckerBreakdownTypedDictIncompatible(params[0], params[1]),
                           codifiedType(matchContext, actual), codifiedType(matchContext, expected)));
            return Optional.of(false);
        }

        if (actual instanceof PyTypedDictType actualTypedDict) {
            // Capture which key fails only while collecting a breakdown; null sink keeps normal matching cheap.
            Ref<PyTypedDictType.@Nullable TypedDictKeyMismatch> keyMismatch = Ref.create();
            Boolean matchResult = PyTypedDictType.match(expected, actualTypedDict, context,
                                                        matchContext.diagnostics != null ? keyMismatch::set : null);
            if (matchResult != null) {
                if (!matchResult) {
                    recordLeaf(matchContext, () -> typedDictStep(matchContext, expected, keyMismatch.get()), () -> {
                        PyTypedDictType.TypedDictKeyMismatch mismatch = keyMismatch.get();
                        if (expected instanceof PyTypedDictType) {
                            return mismatch != null
                                ? typedDictKeyMessage(mismatch)
                                : PyInspectionMessages.problemMessage(
                                    params -> PyLocalize.inspTypeCheckerBreakdownTypedDictIncompatible(params[0], params[1]),
                                    codifiedType(matchContext, actual), codifiedType(matchContext, expected));
                        }
                        else {
                            return typeNotAssignable(matchContext, actual, expected);
                        }
                    });
                }
                return Optional.of(matchResult);
            }
        }

        if (expected instanceof PyLiteralStringType expectedLiteralString) {
            return Optional.of(PyLiteralStringType.match(expectedLiteralString, actual));
        }

        if (expected instanceof PySentinelType || actual instanceof PySentinelType) {
            return Optional.of(false);
        }

        PyClass superClass = expected.getPyClass();
        PyClass subClass = actual.getPyClass();

        // For class definitions with custom metaclasses, always use protocol matching.
        // Python's data model uses type(obj).__iter__ (metaclass method) for dunder protocols on class objects,
        // so even if the class explicitly inherits from e.g., Iterable via a base class, the metaclass method takes priority.
        boolean isDefinitionWithCustomMetaclass = false;
        if (actual.isDefinition()) {
            PyClassLikeType metaClassType = actual.getMetaClassType(context, true);
            isDefinitionWithCustomMetaclass = metaClassType instanceof PyClassType && !PyNames.TYPE.equals(metaClassType.getName());
        }

        if (isProtocol(expected, context) &&
            (!subClass.isSubclass(superClass, context) || isDefinitionWithCustomMetaclass)) {
            return recordFrame(matchContext,
                               () -> PyInspectionMessages.problemMessage(
                                   params -> PyLocalize.inspTypeCheckerBreakdownIncompatibleWithProtocol(params[0], params[1]),
                                   codifiedType(matchContext, actual), codifiedType(matchContext, expected)),
                               () -> new PyMismatchStep.Protocol(codifiedType(matchContext, actual), codifiedType(matchContext, expected)),
                               () -> Optional.of(matchProtocols(expected, actual, matchContext)));
        }

        if (expected.isParameterized()) {
            return Optional.of(matchParameterizedClass(expected, actual, matchContext));
        }

        if (matchClasses(superClass, subClass, context)) {
            if (expected instanceof PyTypingNewType && !expected.equals(actual) && superClass.equals(subClass)) {
                return Optional.of(actual.getAncestorTypes(context).contains(expected));
            }
            return Optional.of(true);
        }
        return Optional.empty();
    }

    private static boolean matchProtocols(PyClassType expected, PyClassType actual, MatchContext matchContext) {
        // See https://typing.python.org/en/latest/spec/generics.html#use-in-protocols
        // > If a protocol uses Self in methods or attribute annotations, then a class Foo is assignable to the protocol
        //   if its corresponding methods and attribute annotations use either Self or Foo or any of Foo’s subclasses.
        //
        // It should be equivalent to replacing Self in the protocol with the Foo class we're matching it with.
        GenericSubstitutions protocolSubstitutions = new GenericSubstitutions();
        protocolSubstitutions.setSelfType(actual.toInstance());
        MatchContext protocolContext = new MatchContext(matchContext.context, protocolSubstitutions, matchContext.reversedSubstitutions);
        protocolContext.diagnostics = matchContext.diagnostics;
        protocolContext.anchor = matchContext.anchor;

        List<Pair<PyTypeMember, List<PyTypeMember>>> matchingMembers = getMatchingProtocolMembers(expected, actual, protocolContext);
        boolean hasMissingMatch = ContainerUtil.exists(matchingMembers, pair -> pair.getSecond().isEmpty());
        if (hasMissingMatch) {
            return false;
        }

        if (expected.isParameterized() && hasGenerics(expected, matchContext.context)) {
            PyClassType concreteExpected = Objects.requireNonNull(
                (PyClassType) substitute(expected, protocolContext.mySubstitutions, protocolContext.context)
            );
            // This match is supposed to succeed since all protocol members were compatible.
            // Effectively, we're just copying substitutions for the terminal type parameters e.g.
            // extracting: T@func -> str
            // from: Iterable[T@func] <- Iterable[str]
            return matchGenericClassesParameterWise(expected, concreteExpected, matchContext);
        }

        return true;
    }

    public static List<Pair<PyTypeMember, List<PyTypeMember>>> getMatchingProtocolMembers(PyClassType expected,
                                                                                         PyClassType actual,
                                                                                         GenericSubstitutions substitutions,
                                                                                         TypeEvalContext context) {
        substitutions.setSelfType(actual.toInstance());
        MatchContext protocolContext = new MatchContext(context, substitutions, false);
        return getMatchingProtocolMembers(expected, actual, protocolContext);
    }

    private static List<Pair<PyTypeMember, List<PyTypeMember>>> getMatchingProtocolMembers(PyClassType expected,
                                                                                          PyClassType actual,
                                                                                          MatchContext protocolContext) {
        TypeEvalContext context = protocolContext.context;
        GenericSubstitutions expectedSubstitutions = collectTypeSubstitutions(expected, context);
        GenericSubstitutions actualSubstitutions = collectTypeSubstitutions(actual, context);
        List<Pair<PyTypeMember, List<PyTypeMember>>> result = new ArrayList<>();

        for (Pair<PyTypeMember, List<PyTypeMember>> protocolAndSubclassElements : inspectProtocolSubclass(expected, actual, context)) {
            PyTypeMember protocolMember = protocolAndSubclassElements.getFirst();
            List<PyTypeMember> subclassElementMembers = protocolAndSubclassElements.getSecond();
            List<PyTypeMember> matchingMembers = new ArrayList<>();
            result.add(Pair.create(protocolMember, matchingMembers));
            if (ContainerUtil.isEmpty(subclassElementMembers)) {
                // Name the kind of member missing (a callable protocol member is a "method", everything else an "attribute").
                PyType memberType = protocolMember.getType();
                boolean isMethod = memberType instanceof PyFunctionType ||
                    (memberType instanceof PyCallableType callableType && callableType.isCallable());
                recordLeaf(protocolContext, () -> {
                    String name = protocolMember.getName();
                    return name != null ? new PyMismatchStep.Missing(name, isMethod) : null;
                }, () -> isMethod
                    ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownMethodMissing(params[0]),
                                                          protocolMember.getName())
                    : PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownMemberMissing(params[0]),
                                                          protocolMember.getName()));
                continue;
            }

            PyType rawProtocolElementType = dropSelfInProtocolMember(expected, protocolMember.getType(), context);
            PyType protocolElementType = substitute(rawProtocolElementType, expectedSubstitutions, context);

            for (PyTypeMember subclassElementMember : subclassElementMembers) {
                boolean isMatchingProtocolMember = recordFrameBool(
                    protocolContext,
                    () -> protocolMemberFailureMessage(protocolMember, subclassElementMembers, context),
                    // The subclass's type for the attribute, so the Attribute step can report the attribute's own types when the
                    // mismatch is buried inside a parameterized type (`list[set[int]]` vs `list[set[None]]`).
                    () -> {
                        String name = protocolMember.getName();
                        if (name == null) {
                            return null;
                        }
                        PyType subclassType = subclassElementType(expected, actual, subclassElementMember, subclassElementMember.getType(),
                                                                  actualSubstitutions, protocolContext);
                        return new PyMismatchStep.Attribute(name,
                                                            subclassType != null ? codifiedType(protocolContext, subclassType) : null,
                                                            codifiedType(protocolContext, protocolElementType));
                    },
                    () -> isMatchingProtocolMember(expected, actual, protocolMember, subclassElementMember, protocolElementType,
                                                   expectedSubstitutions, actualSubstitutions, protocolContext)
                );
                if (isMatchingProtocolMember) {
                    matchingMembers.add(subclassElementMember);
                }
            }
        }

        return result;
    }

    private static boolean isMatchingProtocolMember(PyClassType expected,
                                                    PyClassType actual,
                                                    PyTypeMember protocolMember,
                                                    PyTypeMember subclassElementMember,
                                                    @Nullable PyType protocolElementType,
                                                    GenericSubstitutions expectedSubstitutions,
                                                    GenericSubstitutions actualSubstitutions,
                                                    MatchContext protocolContext) {
        TypeEvalContext context = protocolContext.context;
        boolean isSettableProtocolMember = PyTypeUtil.isSettable(protocolMember, context);
        if (isSettableProtocolMember && !PyTypeUtil.isSettable(subclassElementMember, context)) {
            return false;
        }
        if (protocolMember.isDeletable() && !subclassElementMember.isDeletable()) {
            return false;
        }
        boolean isProtocolMemberClassVar = protocolMember.isClassVar();
        boolean isSubclassMemberClassVar = subclassElementMember.isClassVar();
        if (isSubclassMemberClassVar != isProtocolMemberClassVar) {
            return false;
        }

        PyType subclassElementType = subclassElementType(expected, actual, subclassElementMember, subclassElementMember.getType(),
                                                         actualSubstitutions, protocolContext);
        if (!match(protocolElementType, subclassElementType, protocolContext).orElse(true)) {
            return false;
        }
        if (isSettableProtocolMember) {
            // The write type is contravariant. For a plain attribute, it is the member type, so the member is invariant.
            PyType protocolWriteType = substitute(dropSelfInProtocolMember(expected, writeType(protocolMember, context), context),
                                                  expectedSubstitutions, context);
            PyType subclassWriteType = widenClassObjectLiteral(
                subclassElementType(expected, actual, subclassElementMember, writeType(subclassElementMember, context), actualSubstitutions,
                                    protocolContext),
                context);
            return match(subclassWriteType, protocolWriteType, protocolContext).orElse(true);
        }
        return true;
    }

    /**
     * The subclass member's element type as compared against the protocol: {@code self} bound to {@code actual} and dropped, then
     * the subclass's own type substitutions applied.
     */
    private static @Nullable PyType subclassElementType(PyClassType expected,
                                                        PyClassType actual,
                                                        PyTypeMember subclassElementMember,
                                                        @Nullable PyType subclassElementType,
                                                        GenericSubstitutions actualSubstitutions,
                                                        MatchContext protocolContext) {
        TypeEvalContext context = protocolContext.context;
        PyType result = substituteSelfInProtocolMember(actual, subclassElementType, context);
        result = dropSelfInProtocolMember(expected, result, context);
        result = substitute(result, actualSubstitutions, context);
        // An unannotated attribute can take any value of its class, so its inferred literal type widens to the class.
        return isUnannotatedAttribute(subclassElementMember) ? PyLiteralType.upcastLiteralToClassDeep(result, context) : result;
    }

    private static boolean match(PyClassType expectedProtocol, PyModuleType actualModule, MatchContext matchContext) {
        TypeEvalContext context = matchContext.context;
        PyFile module = actualModule.getModule();

        Map<String, PyElement> moduleElements = new LinkedHashMap<>();
        List<PyElement> topLevelElements = new ArrayList<>();
        topLevelElements.addAll(module.getTopLevelAttributes());
        topLevelElements.addAll(module.getTopLevelFunctions());
        for (PyElement element : topLevelElements) {
            if (element.getProtectionLevel() == ProtectionLevel.PUBLIC) {
                moduleElements.put(element.getName(), element);
            }
        }

        List<Pair<PyTypeMember, List<PyTypeMember>>> protocolElements =
            inspectProtocolSubclass(expectedProtocol, expectedProtocol, context);

        if (protocolElements.size() != moduleElements.size()) {
            return false;
        }

        GenericSubstitutions substitutions = collectTypeSubstitutions(expectedProtocol, context);
        for (Pair<PyTypeMember, List<PyTypeMember>> pair : protocolElements) {
            PsiElement pm = pair.getFirst().getElement();
            if (!(pm instanceof PsiNamedElement namedElement)) {
                continue;
            }
            String name = namedElement.getName();
            PyElement moduleElement = moduleElements.get(name);
            if (moduleElement != null) {
                PyType expectedProtocolMemberType =
                    substitute(dropSelfInProtocolMember(expectedProtocol, pair.getFirst().getType(), context), substitutions, context);
                PyType actualModuleElementType = context.getType((PyTypedElement) moduleElement);
                if (!match(expectedProtocolMemberType, actualModuleElementType, context)) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Returns true when the member is a plain attribute without a type annotation.
     */
    private static boolean isUnannotatedAttribute(PyTypeMember member) {
        if (!(member.getElement() instanceof PyTargetExpression target)) {
            return false;
        }
        return target.getAnnotationValue() == null && target.getTypeCommentAnnotation() == null;
    }

    /**
     * Returns the type that the member accepts on a write.
     * For a property with a setter, it is the type of the value parameter of the setter.
     * For a plain attribute, it is the member type.
     */
    private static @Nullable PyType writeType(PyTypeMember member, TypeEvalContext context) {
        PsiElement setter = member.getSetter();
        if (setter instanceof PyFunction setterFunction && setter != member.getGetter()) {
            PyNamedParameter valueParam = null;
            for (PyParameter parameter : setterFunction.getParameterList().getParameters()) {
                if (parameter instanceof PyNamedParameter namedParameter && !namedParameter.isSelf()) {
                    valueParam = namedParameter;
                }
            }
            if (valueParam != null) {
                return context.getType(valueParam);
            }
        }
        return member.getType();
    }

    /**
     * Widens a class object of a literal type to a class object of the literal class, for example {@code type[Literal[1]]} to {@code type[int]}.
     */
    private static @Nullable PyType widenClassObjectLiteral(@Nullable PyType type, TypeEvalContext context) {
        if (type instanceof PyInstantiableType<?> instantiableType && instantiableType.isDefinition()) {
            PyType widenedInstance = PyLiteralType.upcastLiteralToClassDeep(instantiableType.toInstance(), context);
            return widenedInstance instanceof PyInstantiableType<?> widenedInstantiable ? widenedInstantiable.toClass() : widenedInstance;
        }
        return type;
    }

    private static @Nullable PyType dropSelfInProtocolMember(PyClassType classType, @Nullable PyType elementType, TypeEvalContext context) {
        if (elementType instanceof PyCallableType callableType) {
            if (PyUtil.isInitOrNewMethod(callableType.getCallable()) || !classType.isDefinition()) {
                return callableType.dropSelf(context);
            }
        }
        return elementType;
    }

    /**
     * Binds TypeVars from the self parameter annotation of protocol member to {@code classType}.
     * For an instance, it also replaces {@code Self} in the member with {@code classType}.
     */
    private static @Nullable PyType substituteSelfInProtocolMember(PyClassType classType,
                                                                   @Nullable PyType elementType,
                                                                   TypeEvalContext context) {
        // A member of type `Self` has no self parameter to bind, for example `object.__class__`. Bind `Self` directly.
        if (elementType instanceof PySelfType) {
            GenericSubstitutions selfSubstitutions = new GenericSubstitutions();
            selfSubstitutions.setSelfType(classType.toInstance());
            return substitute(elementType, selfSubstitutions, context);
        }
        if (!(elementType instanceof PyCallableType callableType)) {
            return elementType;
        }
        List<PyCallableParameter> parameters = callableType.getParameters(context);
        if (ContainerUtil.isEmpty(parameters) || !parameters.get(0).isSelf()) {
            return elementType;
        }
        PyType selfParamType = parameters.get(0).getType(context);
        if (selfParamType == null) {
            return elementType;
        }
        if (selfParamType instanceof PySelfType) {
            // A class object keeps its `Self`. Resolving it breaks the match of a class object against a `__call__` protocol.
            if (classType.isDefinition()) {
                return elementType;
            }
            // Without another `Self`, the member needs no substitution and keeps its function type
            if (!hasSelfOutsideSelfParameter(callableType, parameters, context)) {
                return elementType;
            }
        }
        GenericSubstitutions selfSubstitutions = new GenericSubstitutions();
        if (!classType.isDefinition()) {
            selfSubstitutions.setSelfType(classType);
        }

        // An unannotated `self` has the `Self` type. It binds no type variable, so the self match is skipped.
        if (!(selfParamType instanceof PySelfType)) {
            /*
             * Note: intentionally does not propagate [literalInference] into the self-binding sub-context;
             * binding `self` is a separate concern from the conversion of [convertToType], so this match keeps the widening default.
             */
            MatchContext selfMatchContext = new MatchContext(context, selfSubstitutions, false);
            if (!match(selfParamType, classType, selfMatchContext).orElse(true)) {
                return elementType;
            }
        }
        return substitute(elementType, selfSubstitutions, context) instanceof PyCallableType substituted ? substituted : elementType;
    }

    private static boolean hasSelfOutsideSelfParameter(PyCallableType callable,
                                                       List<PyCallableParameter> parameters,
                                                       TypeEvalContext context) {
        return collectGenerics(callable.getReturnType(context), context).getSelf() != null ||
            parameters.stream().skip(1).anyMatch(it -> collectGenerics(it.getType(context), context).getSelf() != null);
    }

    // https://typing.python.org/en/latest/spec/tuples.html#type-compatibility-rules
    private static Optional<Boolean> match(PyTupleType expected,
                                           PyTupleType actual,
                                           MatchContext context) {
        // A NamedTuple is a nominal type: a value is assignable to it only if it is an instance of the same
        // NamedTuple (or a subclass), not an arbitrary structurally-matching tuple such as `(1,)`.
        if (expected instanceof PyNamedTupleType &&
            (!(actual instanceof PyNamedTupleType) || !actual.getPyClass().isSubclass(expected.getPyClass(), context.context))) {
            return Optional.of(false);
        }
        // The fields of a generic named tuple follow from the type arguments of its class, so the type arguments are matched.
        if (expected instanceof PyNamedTupleType && expected.isParameterized()) {
            return Optional.of(matchGenerics(expected, actual, context));
        }
        if (actual.isHomogeneous()) {
            // The type tuple[Any, ...] is consistent with any tuple
            PyType elementType = actual.getIteratedItemType();
            if (isAnyOrUnknown(elementType)) {
                return Optional.of(true);
            }
        }
        // TODO Delegate to UnpackedTupleType here, once it's introduced
        if (!expected.isHomogeneous() && !actual.isHomogeneous()) {
            Predicate<@Nullable PyType> isUnbound =
                type -> (type instanceof PyUnpackedTupleType unpackedTupleType && unpackedTupleType.isUnbound()) || type instanceof PyTypeVarTupleType;
            boolean expectedContainsUnboundTypes =
                ContainerUtil.exists(expected.getElementTypes(), isUnbound::test);
            boolean actualContainsUnboundTypes =
                ContainerUtil.exists(actual.getElementTypes(), isUnbound::test);

            if (actualContainsUnboundTypes && !expectedContainsUnboundTypes) {
                return Optional.of(false);
            }

            return Optional.of(matchTypeParameters(expected, expected.getElementTypes(), actual.getElementTypes(), context));
        }

        if (expected.isHomogeneous() && !actual.isHomogeneous()) {
            PyType expectedElementType = expected.getIteratedItemType();
            return Optional.of(
                PyTypeUtil.toStream(actual.getIteratedItemType())
                    .allMatch(type -> match(expectedElementType, type, context).orElse(true))
            );
        }

        if (!expected.isHomogeneous() && actual.isHomogeneous()) {
            return Optional.of(false);
        }

        return match(expected.getIteratedItemType(), actual.getIteratedItemType(), context);
    }

    private static boolean matchParameterizedClass(PyClassType expected, PyClassType actual, MatchContext context) {
        if (actual instanceof PyTupleType actualTuple) {
            return matchParameterizedClass(expected, actualTuple, context);
        }

        PyClass superClass = expected.getPyClass();
        PyClass subClass = actual.getPyClass();

        return matchClasses(superClass, subClass, context.context) && matchGenerics(expected, actual, context);
    }

    private static boolean matchParameterizedClass(PyClassType expected, PyTupleType actual, MatchContext context) {
        if (!matchClasses(expected.getPyClass(), actual.getPyClass(), context.context)) {
            return false;
        }

        PyType superElementType = expected.getIteratedItemType();
        PyType subElementType = actual.getIteratedItemType();

        return match(superElementType, subElementType, context).orElse(true);
    }

    private static boolean match(PyStructuralType expected, @Nullable PyType actual, TypeEvalContext context) {
        if (actual instanceof PyStructuralType actualStructural) {
            return match(expected, actualStructural);
        }
        if (actual instanceof PyClassType actualClass) {
            return match(expected, actualClass, context);
        }
        if (actual instanceof PyModuleType actualModule) {
            PyFile module = actualModule.getModule();
            if (module.getLanguageLevel().isAtLeast(LanguageLevel.PYTHON37) && definesGetAttr(module, context)) {
                return true;
            }
        }

        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        return !ContainerUtil.exists(expected.getAttributeNames(), attribute ->
            ContainerUtil.isEmpty(Objects.requireNonNull(actual).resolveMember(attribute, null, AccessDirection.READ, resolveContext))
        );
    }

    private static boolean match(PyStructuralType expected, PyStructuralType actual) {
        if (expected.isInferredFromUsages()) {
            return true;
        }
        return expected.getAttributeNames().containsAll(actual.getAttributeNames());
    }

    private static boolean match(PyStructuralType expected, PyClassType actual, TypeEvalContext context) {
        if (overridesGetAttr(actual, context)) {
            return true;
        }
        Set<String> actualAttributes = actual.getMemberNames(true, context);
        return actualAttributes.containsAll(expected.getAttributeNames());
    }

    private static boolean match(PyCallableParameterListType expectedParametersType,
                                 PyCallableParameterListType actualParametersType,
                                 MatchContext matchContext) {
        TypeEvalContext context = matchContext.context;
        List<PyCallableParameter> expectedParameters = expectedParametersType.getParameters();
        List<PyCallableParameter> actualParameters = actualParametersType.getParameters();

        int startIndex = 0;
        if (!expectedParameters.isEmpty() && !actualParameters.isEmpty()) {
            PyCallableParameter firstExpectedParam = expectedParameters.get(0);
            PyCallableParameter firstActualParam = actualParameters.get(0);
            if (firstExpectedParam.isSelf() && firstActualParam.isSelf()) {
                if (!match(firstExpectedParam.getType(context), firstActualParam.getType(context), matchContext).orElse(true)) {
                    return false;
                }
                startIndex = 1;
            }
        }

        // Capture the specific structural reason only while collecting a breakdown; null sink keeps normal matching cheap.
        Ref<@Nullable ProblemMessage> listMismatchReason = Ref.create();
        // Collected only while building a breakdown, to name the offending parameter in the per-parameter message.
        List<@Nullable String> parameterNames = matchContext.diagnostics != null ? new ArrayList<>() : null;
        PyTypeParameterMapping mapping = PyCallableParameterMapping.mapCallableParameters(
            ContainerUtil.subList(expectedParameters, startIndex),
            ContainerUtil.subList(actualParameters, startIndex),
            context,
            matchContext.diagnostics != null ? listMismatchReason::set : null,
            parameterNames
        );
        if (mapping == null) {
            recordLeaf(matchContext, () -> {
                ProblemMessage reason = listMismatchReason.get();
                return reason != null
                    ? reason
                    : PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterListIncompatible());
            });
            return false;
        }
        // The collected names align with the mapped pairs only when every pair came from a plain parameter; a shorter list
        // means a container/variadic reshuffled the mapping, so fall back to the position-less message.
        List<@Nullable String> alignedNames =
            parameterNames != null && parameterNames.size() == mapping.getMappedTypes().size() ? parameterNames : null;
        // actual callable type could accept more general parameter type
        List<Couple<PyType>> mappedTypes = mapping.getMappedTypes();
        for (int index = 0; index < mappedTypes.size(); index++) {
            Couple<PyType> pair = mappedTypes.get(index);
            String parameterName = alignedNames != null ? alignedNames.get(index) : null;
            int oneBasedIndex = index + 1;
            Optional<Boolean> matched = recordFrame(matchContext, () ->
                parameterName != null
                    ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterTypeIncompatibleNamed(params[0]),
                                                          parameterName)
                    : PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterTypeIncompatible()), () ->
                // A callable parameter is contravariant: the actual callable must accept everything the expected one
                // may be called with, so the required type is the expected callable's parameter (`first`) and the
                // narrower provided type is the actual callable's parameter (`second`). Name it, or fall back to its
                // 1-based position for the unnamed positional parameters of a `Callable[[…], …]`.
                new PyMismatchStep.ContravariantParameter(parameterName, oneBasedIndex,
                                                          codifiedContravariantType(matchContext, pair.getFirst()),
                                                          codifiedContravariantType(matchContext, pair.getSecond())), () ->
                matchContext.reverseSubstitutions().reversedSubstitutions
                    ? match(pair.getSecond(), pair.getFirst(), matchContext.reverseSubstitutions())
                    : match(pair.getFirst(), pair.getSecond(), matchContext.reverseSubstitutions())
            );
            if (!matched.orElse(true)) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Boolean> match(PyCallableType expected,
                                           PyCallableType actual,
                                           MatchContext matchContext) {
        if (actual instanceof PyFunctionType actualFunction && expected instanceof PyClassType expectedClass &&
            PyNames.FUNCTION.equals(expectedClass.getName()) &&
            expected.equals(PyBuiltinCache.getInstance(actualFunction.getCallable()).getObjectType(PyNames.FUNCTION))) {
            return Optional.of(true);
        }

        TypeEvalContext context = matchContext.context;

        if (expected instanceof PyClassLikeType expectedClassLike) {
            if (isCallableProtocol(expectedClassLike, context)) {
                List<PyTypeMember> protocolMembers = getProtocolMembers(expectedClassLike, context);
                if (protocolMembers.size() != 1 || !"__call__".equals(protocolMembers.get(0).getName())) {
                    return Optional.of(false);
                }
            }
            else {
                return PyTypingTypeProvider.CALLABLE.equals(expectedClassLike.getClassQName())
                    ? Optional.of(actual.isCallable())
                    : Optional.empty();
            }
        }

        if (expected instanceof PyClassType expectedClass && actual instanceof PyFunctionType actualFunction &&
            isCallableProtocol(expectedClass, context)) {
            List<@Nullable PyType> expectedOverloads =
                ContainerUtil.map(expectedClass.findMember(PyNames.CALL, PyResolveContext.defaultContext(context)), PyTypeMember::getType);
            PyCallable actualCallable = actualFunction.getCallable();
            List<@Nullable PyType> actualOverloads;
            if (actualCallable instanceof PyFunction actualPyFunction) {
                List<@Nullable PyType> overloads =
                    ContainerUtil.map(PyiUtil.getOverloads(actualPyFunction, context), it -> context.getType(it));
                actualOverloads = !overloads.isEmpty() ? overloads : List.of(actual);
            }
            else {
                actualOverloads = List.of(actual);
            }
            return Optional.of(expectedOverloads.stream().allMatch(expectedCall ->
                actualOverloads.stream().anyMatch(actualCall ->
                    match(dropSelfInProtocolMember(expectedClass, expectedCall, context),
                          actualCall, matchContext).orElse(true)
                )
            ));
        }

        if (expected.isCallable() && actual.isCallable()) {
            PyCallableParameterVariadicType expectedParametersType = expected.getParametersType(context);
            PyCallableParameterVariadicType actualParametersType = actual.getParametersType(context);

            boolean allMatched = true;
            if (expectedParametersType != null && actualParametersType != null) {
                if (!match(expectedParametersType, actualParametersType, matchContext).orElse(true)) {
                    // While collecting a breakdown, keep going to record the return-type reason too, so a callable that is
                    // wrong in both a parameter and its return type explains both; when matching normally, short-circuit.
                    if (matchContext.diagnostics == null) {
                        return Optional.of(false);
                    }
                    allMatched = false;
                }
            }
            Optional<Boolean> returnMatched = recordFrame(matchContext,
                                                          () -> PyInspectionMessages.problemMessage(
                                                              params -> PyLocalize.inspTypeCheckerBreakdownReturnTypeIncompatible()),
                                                          () -> PyMismatchStep.Return.INSTANCE,
                                                          () -> match(expected.getReturnType(context), getActualReturnType(actual, context),
                                                                      matchContext));
            if (!returnMatched.orElse(true)) {
                allMatched = false;
            }
            return Optional.of(allMatched);
        }
        return Optional.empty();
    }

    /**
     * Matches the class {@code actual} against the callable {@code expected}. A class used as a callable exposes only the first overload of
     * its constructor. When that overload does not match, each constructor overload is tried.
     */
    private static Optional<Boolean> matchClassAsCallable(PyCallableType expected, PyCallableType actual, MatchContext matchContext) {
        Optional<Boolean> firstOverloadMatch = matchOnCopy(matchContext, it -> match(expected, actual, it));
        if (!firstOverloadMatch.equals(Optional.of(false))) {
            return firstOverloadMatch;
        }
        PyType constructorType =
            PyCallExpressionHelper.createCallableFromClass((PyClassType) actual, PyResolveContext.defaultContext(matchContext.context));
        // A failed match runs again on [matchContext], so that it records the breakdown.
        return constructorType instanceof PyOverloadType
            ? match(expected, constructorType, matchContext)
            : match(expected, actual, matchContext);
    }

    /**
     * Runs {@code body} on a copy of the substitutions and without a breakdown.
     * A successful match keeps the substitutions of the copy. A failed match leaves no trace in {@code matchContext}.
     */
    private static Optional<Boolean> matchOnCopy(MatchContext matchContext, Function<MatchContext, Optional<Boolean>> body) {
        GenericSubstitutions substitutions = matchContext.mySubstitutions.copy(KeyImpl.INSTANCE);
        Optional<Boolean> result = body.apply(new MatchContext(matchContext.context, substitutions, matchContext.reversedSubstitutions));
        if (result.equals(Optional.of(true))) {
            matchContext.mySubstitutions.replaceWith(substitutions, KeyImpl.INSTANCE);
        }
        return result;
    }

    private static @Nullable PyType getActualReturnType(PyCallableType actual, TypeEvalContext context) {
        if (actual instanceof PyCallableTypeImpl) {
            return actual.getReturnType(context);
        }
        PyCallable callable = actual.getCallable();
        if (callable instanceof PyFunction function) {
            return PyUtil.getReturnTypeToAnalyzeAsCallType(function, context);
        }
        return actual.getReturnType(context);
    }

    private static boolean match(List<? extends @Nullable PyType> expected,
                                 List<? extends @Nullable PyType> actual,
                                 MatchContext matchContext) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!match(expected.get(i), actual.get(i), matchContext).orElse(true)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCallableProtocol(PyClassLikeType expected, TypeEvalContext context) {
        return isProtocol(expected, context) && expected.getMemberNames(false, context).contains(PyNames.CALL);
    }

    private static @Nullable PyTupleType widenUnionOfTuplesToTupleOfUnions(PyUnionType unionType, int elementCount) {
        boolean consistsOfSameSizeTuples = unionType.getMembers().stream().allMatch(
            member -> member instanceof PyTupleType tupleType && !tupleType.isHomogeneous() && elementCount == tupleType.getElementCount()
        );
        if (!consistsOfSameSizeTuples) {
            return null;
        }
        List<@Nullable PyType> newTupleElements = new ArrayList<>(elementCount);
        for (int index = 0; index < elementCount; index++) {
            List<@Nullable PyType> elementTypes = new ArrayList<>();
            for (PyType member : unionType.getMembers()) {
                if (member instanceof PyTupleType tupleType) {
                    elementTypes.add(tupleType.getElementType(index));
                }
            }
            newTupleElements.add(PyUnionType.unionOrUnknown(elementTypes));
        }
        PyClass tupleClass = ((PyTupleType) ContainerUtil.getFirstItem(unionType.getMembers())).getPyClass();
        return new PyTupleType(tupleClass, newTupleElements, false);
    }

    private static boolean matchGenerics(PyClassType expected, PyClassType actual, MatchContext context) {
        if (actual.isParameterized() && expected.getPyClass().equals(actual.getPyClass())) {
            return matchGenericClassesParameterWise(expected, actual, context);
        }

        PyClassType expectedGenericType = findGenericDefinitionType(expected.getPyClass(), context.context);
        if (expectedGenericType != null) {
            GenericSubstitutions actualSubstitutions = collectTypeSubstitutions(actual, context.context);
            PyClassType concreteExpected =
                Objects.requireNonNull((PyClassType) substitute(expectedGenericType, actualSubstitutions, context.context));
            return matchGenericClassesParameterWise(expected, concreteExpected, context);
        }
        return true;
    }

    // TODO Make it a part of PyClassType interface

    /**
     * Extract all type substitutions from a generic class instance. For instance, for the type of {@code x}
     *
     * <pre>{@code class Base[T1, T2]: ... class Sub[T3](Base[int, T3]): ... x: Sub[str] }</pre>
     * <p>
     * <p>
     * we should extract
     *
     * <pre>{@code T1@Base -> int T2@Base -> Sub@T3 Sub@T3 -> str }</pre>
     */
    public static GenericSubstitutions collectTypeSubstitutions(PyClassType classType, TypeEvalContext context) {
        GenericSubstitutions result = new GenericSubstitutions();
        // Collect substitutions from ancestor classes. In the example above these are: T1@Base -> int and T2@Base -> Sub@T3
        for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
            Map<PyType, PyType> substitutionsFromClassDefinition = provider.getGenericSubstitutions(classType.getPyClass(), context);
            for (Map.Entry<PyType, PyType> entry : substitutionsFromClassDefinition.entrySet()) {
                PyType key = entry.getKey();
                PyType value = entry.getValue();
                if (key instanceof PyTypeVarType typeVarType) {
                    result.putTypeVar(typeVarType, Ref.create(value), KeyImpl.INSTANCE);
                }
                else if (key instanceof PyTypeVarTupleType typeVarTupleType) {
                    result.putTypeVarTuple(typeVarTupleType, (PyPositionalVariadicType) value, KeyImpl.INSTANCE);
                }
                else if (key instanceof PyParamSpecType paramSpecType) {
                    if (value instanceof PyCallableParameterVariadicType variadicType) {
                        result.putParamSpec(paramSpecType, variadicType, KeyImpl.INSTANCE);
                    }
                }
            }
            // Collect own type parameters. In the example above these are: Sub@T3 -> str
            PyClassType genericDefinitionType = provider.getGenericType(classType.getPyClass(), context) instanceof PyClassType t ? t : null;
            // TODO Re-use PyTypeParameterMapping, at the moment C[*Ts] <- C leads to *Ts being mapped to *tuple[], which breaks inference later on
            if (genericDefinitionType != null) {
                List<@Nullable PyType> definitionTypeParameters = genericDefinitionType.getTypeArguments();

                // Inside method bodies, where Self type can appear, we map class' own type parameters to themseleves,
                // not to consider them unbound. For instance, here
                //
                // class B[T]:...
                // class C[T2](B[T2]):
                //     attr: T
                //     def m(self):
                //         self.attr
                //
                // when collecting substitutions for `self` in `m`, we will map T@B -> T2@C, but T2@C -> T2@C.
                //
                // See Py3TypeTest.testApplyingSuperSubstitutionToBoundedGenericClass
                // and Py3TypeTest#testApplyingSuperSubstitutionToGenericClass
                if (classType instanceof PySelfType) {
                    mapTypeParametersToSubstitutions(
                        result, definitionTypeParameters, definitionTypeParameters,
                        PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY
                    );
                }
                else if (!classType.isParameterized()) {
                    for (PyType typeParameter : definitionTypeParameters) {
                        if (typeParameter instanceof PyTypeVarType typeVarType) {
                            result.putTypeVar(typeVarType, Ref.create(PyAnyType.getUnknown()), KeyImpl.INSTANCE);
                        }
                        else if (typeParameter instanceof PyTypeVarTupleType typeVarTupleType) {
                            result.putTypeVarTuple(typeVarTupleType, null, KeyImpl.INSTANCE);
                        }
                        else if (typeParameter instanceof PyParamSpecType paramSpecType) {
                            result.putParamSpec(paramSpecType, null, KeyImpl.INSTANCE);
                        }
                    }
                }
                else {
                    List<@Nullable PyType> elementTypes = classType.getTypeArguments();
                    List<@Nullable PyType> typeParameters = definitionTypeParameters;

                    if (shouldExpandElementwiseCollectionType(classType)) {
                        /* treat tuples as `class tuple[_T_co#1, _T_co#2, ..., _T_co#n](Sequence[_T_co]): ...`. @see [expandTupleTypeParameters]. */
                        List<@Nullable PyType> actualArguments = new ArrayList<>(classType.getTypeArguments());
                        actualArguments.removeAll(definitionTypeParameters);
                        List<@Nullable PyType> expandedTypeParameters = expandTupleTypeParameters(definitionTypeParameters, actualArguments.size());
                        if (expandedTypeParameters != null) {
                            List<@Nullable PyType> unionTypes = new ArrayList<>();
                            for (PyType et : actualArguments) {
                                if (et instanceof PyUnpackedTupleType unpackedTupleType) {
                                    unionTypes.addAll(unpackedTupleType.getElementTypes());
                                }
                                else {
                                    unionTypes.add(et);
                                }
                            }
                            elementTypes = new ArrayList<>();
                            elementTypes.add(PyUnionType.unionOrUnknown(unionTypes));
                            elementTypes.addAll(actualArguments);
                            typeParameters = new ArrayList<>(definitionTypeParameters);
                            typeParameters.addAll(expandedTypeParameters);
                        }
                    }
                    mapTypeParametersToSubstitutions(
                        result, typeParameters, elementTypes,
                        PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY
                    );
                }
            }
            if (!result.getTypeVars().isEmpty() || !result.getTypeVarTuples().isEmpty() || !result.getParamSpecs().isEmpty()) {
                return result;
            }
        }
        return result;
    }

    /**
     * @see #expandTupleTypeParameters
     */
    private static boolean shouldExpandElementwiseCollectionType(PyClassType classType) {
        // The type arguments of a named tuple belong to its class, not to its elements.
        if (classType instanceof PyTupleType tupleType && !(classType instanceof PyNamedTupleType)) {
            return !tupleType.isHomogeneous();
        }
        return false;
    }

    /**
     * Creates {@code size} synthetic copies of the single type variable in {@code definitionTypeParameters}.
     * Each copy gets the name {@code "<name>#<index>"} to keep the copies distinct. Returns null when the expansion is not possible.
     * <p>
     * Note that Python tuple types are defined as {@code class tuple(Sequence[_T_co]): ...}. This models the upwards type hierarchy.
     * However, tuples can be used as {@code tuple[int, str]} which is not represented by that definition. The expansion will create additional
     * type variables for tuples that can be roughly understood as {@code class tuple[_T_co#1, _T_co#2](Sequence[_T_co]): ...}
     * with {@code _T_co = _T_co#1 | _T_co#2 | ... | _T_co#n}.
     * <p>
     * Also note the explanations at the use-site of expanded type parameters: [ConstraintReducer.subtract]
     */
    @SuppressWarnings("unchecked")
    static @Nullable List<@Nullable PyType> expandTupleTypeParameters(List<? extends @Nullable PyType> definitionTypeParameters, int size) {
        if (definitionTypeParameters.size() != 1 || size <= 1) {
            return null;
        }
        if (!(definitionTypeParameters.get(0) instanceof PyTypeVarType typeVar)) {
            return null;
        }
        List<@Nullable PyType> result = new ArrayList<>(size);
        for (int index = 1; index <= size; index++) {
            result.add(
                // cast is necessary to select the correct constructor
                new PyTypeVarTypeImpl(
                    typeVar.getName() + "#" + index,
                    typeVar.getConstraints(),
                    typeVar.getBound(),
                    (Ref<PyType>) typeVar.getDefaultType(),
                    typeVar.getVariance()
                )
                    .withScopeOwner(typeVar.getScopeOwner())
                    .withDeclarationElement(typeVar.getDeclarationElement())
            );
        }
        return result;
    }

    public static @Nullable PyClassType findGenericDefinitionType(PyClass pyClass, boolean isDefinition, TypeEvalContext context) {
        PyClassType genericDefinitionType = findGenericDefinitionType(pyClass, context);
        if (genericDefinitionType == null) {
            return null;
        }
        if (isDefinition) {
            genericDefinitionType.toClass();
        }
        return genericDefinitionType;
    }

    public static @Nullable PyClassType findGenericDefinitionType(PyClass pyClass, TypeEvalContext context) {
        return PyTypeProvider.EP_NAME.computeSafeIfAny(it ->
            it.getGenericType(pyClass, context) instanceof PyClassType definitionType && definitionType.isParameterized() ? definitionType : null
        );
    }

    private static boolean matchGenericClassesParameterWise(PyClassType expected,
                                                            PyClassType actual,
                                                            MatchContext context) {
        if (expected.equals(actual)) {
            return true;
        }
        if (!expected.getPyClass().equals(actual.getPyClass())) {
            return false;
        }
        List<@Nullable PyType> expectedElementTypes = expected.getTypeArguments();
        List<@Nullable PyType> actualElementTypes = actual.getTypeArguments();
        if (context.reversedSubstitutions) {
            return matchTypeParameters(actual, actualElementTypes, expectedElementTypes, context.resetSubstitutions());
        }
        else {
            return matchTypeParameters(expected, expectedElementTypes, actualElementTypes, context);
        }
    }

    private static boolean matchTypeParameters(@Nullable PyClassType genericType,
                                               List<? extends @Nullable PyType> expectedTypeParameters,
                                               List<? extends @Nullable PyType> actualTypeParameters,
                                               MatchContext context) {
        if (Registry.is("python.subtypechecks.respect.variance", true)) {
            return matchTypeParametersRespectVariance(genericType, expectedTypeParameters, actualTypeParameters, context);
        }
        else {
            return matchTypeParametersIgnoreVariance(genericType, expectedTypeParameters, actualTypeParameters, context);
        }
    }

    private static boolean matchTypeParametersIgnoreVariance(@Nullable PyClassType genericType,
                                                             List<? extends @Nullable PyType> expectedTypeParameters,
                                                             List<? extends @Nullable PyType> actualTypeParameters,
                                                             MatchContext context) {
        PyTypeParameterMapping mapping =
            PyTypeParameterMapping.mapByShape(expectedTypeParameters, actualTypeParameters, PyTypeParameterMapping.Option.USE_DEFAULTS);
        if (mapping == null) {
            return false;
        }
        List<Couple<PyType>> mappedTypes = mapping.getMappedTypes();
        for (int typeArgIndex = 0; typeArgIndex < mappedTypes.size(); typeArgIndex++) {
            Couple<PyType> pair = mappedTypes.get(typeArgIndex);
            // Record the per-argument frame (naming the type variable + owner when resolvable, else the position; expected
            // = the target's arg, `first`) so the breakdown names and drills in even on this variance-ignoring path; the
            // match direction still respects reversedSubstitutions.
            // The type parameter only names the breakdown frame, so skip the lookup on the plain matching path.
            PyTypeParameterType typeParameter = context.diagnostics != null ? findTypeParameter(genericType, typeArgIndex, context) : null;
            boolean matched = recordTypeArgumentFrame(context, typeArgIndex, typeParameter, genericType, pair.getFirst(), pair.getSecond(), () ->
                (context.reversedSubstitutions
                 ? match(pair.getSecond(), pair.getFirst(), context)
                 : match(pair.getFirst(), pair.getSecond(), context)).orElse(true)
            );
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchTypeParametersRespectVariance(@Nullable PyClassType genericType,
                                                              List<? extends @Nullable PyType> expectedTypeParameters,
                                                              List<? extends @Nullable PyType> actualTypeParameters,
                                                              MatchContext context) {
        PyTypeParameterMapping mapping =
            PyTypeParameterMapping.mapByShape(expectedTypeParameters, actualTypeParameters, PyTypeParameterMapping.Option.USE_DEFAULTS);
        if (mapping == null) {
            return false;
        }
        List<Couple<PyType>> mappedTypes = mapping.getMappedTypes();
        for (int typeArgIndex = 0; typeArgIndex < mappedTypes.size(); typeArgIndex++) {
            Couple<PyType> pair = mappedTypes.get(typeArgIndex);
            PyType first = pair.getFirst();
            PyType second = pair.getSecond();
            PyTypeParameterType typeParameter = findTypeParameter(genericType, typeArgIndex, context);
            PyVariance variance = typeParameter == null
                ? PyVariance.COVARIANT
                : PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeParameter, context.context);

            boolean matched = context.reversedSubstitutions
                ? matchCapturedType(typeParameter, genericType, typeArgIndex, variance, second, first, context)
                : matchCapturedType(typeParameter, genericType, typeArgIndex, variance, first, second, context);
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wraps {@code body} in the "Type argument N has an incompatible type" frame for the type argument {@code typeArgIndex}.
     * {@code expected}/{@code actual} are the argument's types at this level (in display orientation — expected is the target's,
     * regardless of which direction {@code body} matches), recorded so the breakdown can drill in readably.
     */
    private static boolean recordTypeArgumentFrame(MatchContext context,
                                                   int typeArgIndex,
                                                   @Nullable PyTypeParameterType typeParameter,
                                                   @Nullable PyClassType genericType,
                                                   @Nullable PyType expected,
                                                   @Nullable PyType actual,
                                                   BooleanSupplier body) {
        return recordFrameBool(context, () -> {
            // Name the parameter and its owner (`` `T` of `A` ``) when they can be resolved — the owner as a clickable
            // reference, the type variable as a plain code span — else fall back to the 1-based position.
            String name = typeParameter != null ? typeParameter.getName() : null;
            PyClass ownerClass = genericType != null ? genericType.getPyClass() : null;
            if (name != null && ownerClass != null) {
                return PyInspectionMessages.problemMessage(
                    params -> PyLocalize.inspTypeCheckerBreakdownTypeArgumentNamed(params[0], params[1]),
                    name, CodifiedParam.ofReference(ownerClass));
            }
            else {
                return PyInspectionMessages.problemMessage(
                    params -> PyLocalize.inspTypeCheckerBreakdownTypeArgument(params[0]),
                    typeArgIndex + 1);
            }
        }, () ->
            // Record the argument's types at this level (display orientation: expected is the target's) so the prose
            // breakdown can drill in readably (`expected set[int], but got set[None]`).
            new PyMismatchStep.TypeArgument(typeArgIndex + 1, codifiedType(context, actual), codifiedType(context, expected)), body);
    }

    /**
     * The declared type parameter at {@code typeArgumentIndex} of {@code genericType}'s class, or null when there isn't one
     * (no generic class, a plain tuple, or an index past the declared parameters). Used both to find the
     * variance and to name the parameter in breakdowns.
     */
    private static @Nullable PyTypeParameterType findTypeParameter(@Nullable PyClassType genericType,
                                                                   int typeArgumentIndex,
                                                                   MatchContext context) {
        if (genericType == null) {
            return null;
        }
        if (genericType instanceof PyTupleType && !(genericType instanceof PyNamedTupleType)) {
            return null;
        }
        PyClassType genericDefType = findGenericDefinitionType(genericType.getPyClass(), context.context);
        if (genericDefType == null) {
            return null;
        }
        List<@Nullable PyType> typeArguments = genericDefType.getTypeArguments();
        return typeArgumentIndex >= 0 && typeArgumentIndex < typeArguments.size() &&
            typeArguments.get(typeArgumentIndex) instanceof PyTypeParameterType typeParameterType ? typeParameterType : null;
    }

    private static boolean matchCapturedType(@Nullable PyTypeParameterType typeParameter,
                                             @Nullable PyClassType genericType,
                                             int typeArgIndex,
                                             PyVariance variance,
                                             @Nullable PyType expectedType,
                                             @Nullable PyType actualType,
                                             MatchContext context) {
        if (expectedType instanceof PyTypeParameterType && actualType instanceof PyTypeParameterType) {
            return recordTypeArgumentFrame(context, typeArgIndex, typeParameter, genericType, expectedType, actualType,
                                           () -> match(expectedType, actualType, context).orElse(false));
        }

        return switch (variance) {
            case INVARIANT -> {
                // First leg: the actual type argument must be assignable to the expected one. If it isn't, that's an
                // ordinary subtype failure whose own breakdown ("X is not assignable to Y") is the useful reason, so
                // run it under the generic type-argument frame.
                boolean assignable = recordTypeArgumentFrame(context, typeArgIndex, typeParameter, genericType, expectedType, actualType,
                                                             () -> match(expectedType, actualType, context).orElse(false));
                if (!assignable) {
                    yield false;
                }
                else {
                    // Invariance also requires the reverse direction. When only this leg fails, the actual is a strict
                    // subtype (e.g. `list[bool]` where `list[int]` is expected); recording the reversed leg would read
                    // backwards ("int is not assignable to bool"), so discard it and emit one invariance-specific reason.
                    boolean sameType = isUnboundTypeParameterOnActualSide(expectedType, context) ||
                        withoutRecording(context, () -> match(actualType, expectedType, context).orElse(false));
                    if (!sameType) {
                        recordLeaf(context, () -> {
                            // The prose form only earns the educational "even though … is a subtype of …" clause here, on the
                            // surprising path: the forward `assignable` leg already succeeded, so the actual argument IS a
                            // subtype of the expected one and invariance is the sole reason. Requires the owner class.
                            PyClass ownerClass = genericType != null ? genericType.getPyClass() : null;
                            if (ownerClass != null) {
                                String typeVarName = typeParameter != null && typeParameter.getName() != null
                                    ? typeParameter.getName()
                                    : String.valueOf(typeArgIndex + 1);
                                return new PyMismatchStep.Invariant(typeVarName,
                                                                    CodifiedParam.ofReference(ownerClass),
                                                                    codifiedType(context, actualType), codifiedType(context, expectedType),
                                                                    readOnlyAlternativeFor(ownerClass, typeArgIndex));
                            }
                            return null;
                        }, () -> {
                            // Name the offending type variable and its owner (e.g. "`_T` of `list`"); fall back to the 1-based
                            // position only if the declared parameter can't be resolved (it always can in the invariant case).
                            // The owner class is rendered as a clickable reference; the type variable stays a plain code span
                            // (the tooltip link handler resolves only classes and functions, so a type-variable link is dead).
                            String typeVarName = typeParameter != null && typeParameter.getName() != null
                                ? typeParameter.getName()
                                : String.valueOf(typeArgIndex + 1);
                            PyClass ownerClass = genericType != null ? genericType.getPyClass() : null;
                            Object owner = ownerClass != null ? CodifiedParam.ofReference(ownerClass) : "";
                            return PyInspectionMessages.problemMessage(
                                params -> PyLocalize.inspTypeCheckerBreakdownTypeArgumentInvariant(params[0], params[1], params[2], params[3]),
                                typeVarName, owner,
                                codifiedType(context, actualType), codifiedType(context, expectedType));
                        });
                    }
                    yield sameType;
                }
            }
            case COVARIANT -> recordTypeArgumentFrame(context, typeArgIndex, typeParameter, genericType, expectedType, actualType,
                                                      () -> match(expectedType, actualType, context).orElse(false));
            case CONTRAVARIANT -> recordTypeArgumentFrame(context, typeArgIndex, typeParameter, genericType, expectedType, actualType,
                                                          () -> match(actualType, expectedType, context.reverseSubstitutions()).orElse(false));
            case BIVARIANT -> recordTypeArgumentFrame(context, typeArgIndex, typeParameter, genericType, expectedType, actualType,
                                                      () -> match(expectedType, actualType, context).orElse(false));
            default -> false;
        };
    }

    /**
     * Returns true if {@code type} is a type parameter that the reverse leg of an invariance check would see on its actual side.
     * The forward leg has already bound it. {@link #matchImpl} treats such a {@link PyTypeVarType} as unknown (see {@link #containsAny}).
     * A {@link PyTypeVarTupleType} or a {@link PyParamSpecType} gets the same treatment here, because the dedicated matchers
     * for a positional variadic or a {@code Concatenate} on the expected side run before that check and reject it.
     */
    private static boolean isUnboundTypeParameterOnActualSide(@Nullable PyType type, MatchContext context) {
        return !context.reversedSubstitutions &&
            (type instanceof PyTypeVarType || type instanceof PyTypeVarTupleType || type instanceof PyParamSpecType);
    }

    private static boolean matchNumericTypes(@Nullable PyType expected, @Nullable PyType actual) {
        if (expected instanceof PyClassType expectedClass && actual instanceof PyClassType actualClass) {
            String superName = expectedClass.getPyClass().getName();
            String subName = actualClass.getPyClass().getName();
            boolean subIsBool = "bool".equals(subName);
            boolean subIsInt = PyNames.TYPE_INT.equals(subName);
            boolean subIsLong = PyNames.TYPE_LONG.equals(subName);
            boolean subIsFloat = PyNames.TYPE_FLOAT.equals(subName);
            boolean subIsComplex = PyNames.TYPE_COMPLEX.equals(subName);
            if (superName == null || subName == null ||
                superName.equals(subName) ||
                (PyNames.TYPE_INT.equals(superName) && subIsBool) ||
                ((PyNames.TYPE_LONG.equals(superName) || PyNames.ABC_INTEGRAL.equals(superName)) && (subIsBool || subIsInt)) ||
                ((PyNames.TYPE_FLOAT.equals(superName) || PyNames.ABC_REAL.equals(superName)) && (subIsBool || subIsInt || subIsLong)) ||
                ((PyNames.TYPE_COMPLEX.equals(superName) || PyNames.ABC_COMPLEX.equals(superName)) &&
                    (subIsBool || subIsInt || subIsLong || subIsFloat)) ||
                (PyNames.ABC_NUMBER.equals(superName) && (subIsBool || subIsInt || subIsLong || subIsFloat || subIsComplex))
            ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compares an overload type against a callable class/protocol's __call__ type.
     * Returns true if all expected overload items have a match in the actual overload items.
     */
    private static boolean matchOverloadWithCallable(PyOverloadType overloadType,
                                                     PyClassLikeType callableType,
                                                     MatchContext context,
                                                     boolean expectedIsOverload) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context.context);
        List<? extends RatedResolveResult> resolveResults = callableType.resolveMember(PyNames.CALL, null, AccessDirection.READ, resolveContext);
        if (ContainerUtil.isEmpty(resolveResults)) {
            return false;
        }

        PsiElement element = resolveResults.get(0).getElement();
        PyType callType = element instanceof PyTypedElement typedElement ? context.context.getType(typedElement) : null;

        if (callableType instanceof PyClassType classType) {
            callType = dropSelfInProtocolMember(classType, callType, context.context);
        }

        if (callType instanceof PyOverloadType callOverloadType) {
            // If the __call__ is overloaded, compare overload types (subset matching)
            return overloadType.getItems().stream().allMatch(expectedItem ->
                callOverloadType.getItems().stream().anyMatch(actualItem ->
                    match(expectedItem, actualItem, context).orElse(false)
                )
            );
        }
        else if (callType instanceof PyCallableType callCallableType) {
            // If __call__ is not overloaded, check if any overload matches the single callable
            return overloadType.getItems().stream().anyMatch(item -> {
                // Match with correct argument order based on which is expected
                if (expectedIsOverload) {
                    return match(item, callCallableType, context).orElse(false);
                }
                else {
                    return match(callCallableType, item, context).orElse(false);
                }
            });
        }
        else {
            return false;
        }
    }

    public static boolean containsAny(@Nullable PyType type, TypeEvalContext context) {
        return containsAny(type, true, context);
    }

    public static boolean containsAny(@Nullable PyType type, boolean genericsAreUnknown, TypeEvalContext context) {
        // TODO Don't consider other type parameters unknown (PY-85653)
        // Since Self is always bound, don't consider it unknown, e.g. in Py3TypeCheckerInspectionTest.testSelfAssignedToOtherTypeBad
        if (isAnyOrUnknown(type) || (genericsAreUnknown && type instanceof PyTypeParameterType && !(type instanceof PySelfType))) {
            return true;
        }
        if (type instanceof PyUnionType unionType) {
            if (!PyUnionType.isStrictSemanticsEnabled()) {
                return unionType.getMembers().stream().anyMatch(it -> containsAny(it, genericsAreUnknown, context));
            }
            else {
                return unionType.getMembers().stream().allMatch(it -> containsAny(it, genericsAreUnknown, context));
            }
        }
        if (type instanceof PyUnsafeUnionType unsafeUnionType) {
            return unsafeUnionType.getMembers().stream().anyMatch(it -> containsAny(it, genericsAreUnknown, context));
        }
        if (type instanceof PyIntersectionType intersectionType) {
            return intersectionType.getMembers().stream().anyMatch(it -> containsAny(it, genericsAreUnknown, context));
        }
        return false;
    }

    /**
     * @deprecated use containsAny
     */
    @Deprecated
    public static boolean isUnknown(@Nullable PyType type, TypeEvalContext context) {
        return containsAny(type, context);
    }

    /**
     * @deprecated use containsAny
     */
    @Deprecated
    public static boolean isUnknown(@Nullable PyType type, boolean genericsAreUnknown, TypeEvalContext context) {
        return containsAny(type, genericsAreUnknown, context);
    }

    @SuppressWarnings("unchecked")
    public static GenericSubstitutions getSubstitutionsWithUnresolvedReturnGenerics(PyCallableType callableType,
                                                                                    @Nullable PyType returnType,
                                                                                    @Nullable GenericSubstitutions substitutions,
                                                                                    TypeEvalContext context) {
        if (context instanceof TypeEvalContextImpl.TemporaryContext temporaryContext && temporaryContext.getKeepUncapturedTypeParameters()) {
            return substitutions != null ? substitutions : new GenericSubstitutions();
        }

        GenericSubstitutions existingSubstitutions = substitutions != null ? substitutions : new GenericSubstitutions();

        List<PyTypeParameterType> typeParameters = callableType.getTypeParameters(context);
        if (typeParameters != null) {
            for (PyTypeParameterType typeParameter : typeParameters) {
                if (typeParameter instanceof PyTypeVarType typeVarType) {
                    existingSubstitutions.putTypeVar(typeVarType, (Ref<@Nullable PyType>) typeVarType.getDefaultType(), KeyImpl.INSTANCE, true);
                }
                else if (typeParameter instanceof PyTypeVarTupleType typeVarTupleType) {
                    // TODO consulo: Ref.deref missing
                    Ref<PyPositionalVariadicType> defaultTypeRef = typeVarTupleType.getDefaultType();
                    existingSubstitutions.putTypeVarTuple(typeVarTupleType, defaultTypeRef != null ? defaultTypeRef.get() : null, KeyImpl.INSTANCE, true);
                }
                else if (typeParameter instanceof PyParamSpecType paramSpecType) {
                    // TODO consulo: Ref.deref missing
                    Ref<PyType> defaultTypeRef = paramSpecType.getDefaultType();
                    existingSubstitutions.putParamSpec(paramSpecType,
                                                       defaultTypeRef != null && defaultTypeRef.get() instanceof PyCallableParameterVariadicType v ? v : null,
                                                       KeyImpl.INSTANCE, true);
                }
            }
        }

        // Restrict defaulting to `callableType`-scoped type parameters.
        GenericsImpl resolvableTypeParams = new GenericsImpl();
        PyCallable callable = callableType.getCallable();
        if (callable != null) {
            List<PyCallableParameter> parameters = callableType.getParameters(context);
            if (parameters != null) {
                for (PyCallableParameter parameter : parameters) {
                    collectGenerics(parameter.getArgumentType(context), context, resolvableTypeParams);
                }
            }
            resolvableTypeParams.typeVars.removeIf(it -> it.getScopeOwner() != callable);
            resolvableTypeParams.typeVarTuples.removeIf(it -> it.getScopeOwner() != callable);
            resolvableTypeParams.paramSpecs.removeIf(it -> it.getScopeOwner() != callable);
        }

        return substituteUnboundTypeVarsWithDefaultOrAny(returnType, resolvableTypeParams, existingSubstitutions, context);
    }

    @SuppressWarnings("unchecked")
    private static GenericSubstitutions substituteUnboundTypeVarsWithDefaultOrAny(@Nullable PyType targetType,
                                                                                  Generics resolvableTypeParams,
                                                                                  @Nullable GenericSubstitutions substitutions,
                                                                                  TypeEvalContext context) {
        if (context instanceof TypeEvalContextImpl.TemporaryContext temporaryContext && temporaryContext.getKeepUncapturedTypeParameters()) {
            return substitutions != null ? substitutions : new GenericSubstitutions();
        }

        GenericSubstitutions existingSubstitutions = substitutions != null ? substitutions : new GenericSubstitutions();
        Generics requiredTypeParams = collectGenerics(targetType, context);
        // TODO Handle unmatched TypeVarTuples here as well
        if (requiredTypeParams.getTypeVars().isEmpty() && requiredTypeParams.getParamSpecs().isEmpty()) {
            return existingSubstitutions;
        }

        for (PyTypeVarType typeVar : requiredTypeParams.getTypeVars()) {
            boolean isResolvable = resolvableTypeParams.getTypeVars().contains(typeVar) ||
                resolvableTypeParams.getTypeVars().contains(invert(typeVar));
            boolean isAlreadyBound = existingSubstitutions.getTypeVars().containsKey(typeVar) ||
                existingSubstitutions.getTypeVars().containsKey(invert(typeVar));
            if (isResolvable && !isAlreadyBound) {
                if (typeVar.getDefaultType() != null) {
                    existingSubstitutions.putTypeVar(typeVar, (Ref<@Nullable PyType>) typeVar.getDefaultType(), KeyImpl.INSTANCE);
                }
                else {
                    existingSubstitutions.putTypeVar(typeVar, Ref.create(PyAnyType.getUnknown()), KeyImpl.INSTANCE);
                }
            }
        }
        for (PyParamSpecType paramSpecType : requiredTypeParams.getParamSpecs()) {
            boolean isResolvable = resolvableTypeParams.getParamSpecs().contains(paramSpecType);
            boolean isAlreadyBound = existingSubstitutions.getParamSpecs().containsKey(paramSpecType);
            if (isResolvable && !isAlreadyBound) {
                if (paramSpecType.getDefaultType() != null) {
                    // TODO consulo: Ref.deref missing
                    existingSubstitutions.putParamSpec(paramSpecType,
                                                       paramSpecType.getDefaultType().get() instanceof PyCallableParameterVariadicType v ? v : null,
                                                       KeyImpl.INSTANCE);
                }
                else {
                    existingSubstitutions.putParamSpec(paramSpecType, new PyCallableParameterListTypeImpl(
                        List.of(PyCallableParameterImpl.positionalContainerNonPsi("args", PyAnyType.getUnknown()),
                                PyCallableParameterImpl.keywordContainerNonPsi("kwargs", PyAnyType.getUnknown()))), KeyImpl.INSTANCE
                    );
                }
            }
        }
        return existingSubstitutions;
    }

    private static PyTypeVarType invert(PyTypeVarType typeVar) {
        return typeVar.isDefinition() ? typeVar.toInstance() : typeVar.toClass();
    }

    private static PyType invert(PyInstantiableType<?> type) {
        return type.isDefinition() ? type.toInstance() : type.toClass();
    }

    public static boolean hasGenerics(@Nullable PyType type, TypeEvalContext context) {
        return !collectGenerics(type, context).isEmpty();
    }

    public static Generics collectGenerics(@Nullable PyType type, TypeEvalContext context) {
        GenericsImpl result = new GenericsImpl();
        collectGenerics(type, context, result);
        return result;
    }

    private static void collectGenerics(@Nullable PyType type,
                                        TypeEvalContext context,
                                        GenericsImpl generics) {
        PyRecursiveTypeVisitor.traverse(type, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
            @Override
            public PyRecursiveTypeVisitor.@Nullable Traversal visitPyTypeVarType(PyTypeVarType typeVarType) {
                generics.typeVars.add(typeVarType);
                return super.visitPyTypeVarType(typeVarType);
            }

            @Override
            public PyRecursiveTypeVisitor.@Nullable Traversal visitPyTypeVarTupleType(PyTypeVarTupleType typeVarTupleType) {
                generics.typeVarTuples.add(typeVarTupleType);
                return super.visitPyTypeVarTupleType(typeVarTupleType);
            }

            @Override
            public PyRecursiveTypeVisitor.@Nullable Traversal visitPySelfType(PySelfType selfType) {
                generics.self = selfType;
                return super.visitPySelfType(selfType);
            }

            @Override
            public PyRecursiveTypeVisitor.@Nullable Traversal visitPyParamSpecType(PyParamSpecType paramSpecType) {
                generics.paramSpecs.add(paramSpecType);
                return super.visitPyParamSpecType(paramSpecType);
            }

            @Override
            public PyRecursiveTypeVisitor.@Nullable Traversal visitPyTypeParameterType(PyTypeParameterType typeParameterType) {
                // Record a type variable in its instance form, so that a variable used both as `T` and as `type[T]`
                // is the same parameter rather than two distinct ones
                PyTypeParameterType normalized = typeParameterType instanceof PyTypeVarType typeVarType && typeVarType.isDefinition()
                    ? typeVarType.toInstance()
                    : typeParameterType;
                generics.allTypeParameters.add(normalized);
                return PyRecursiveTypeVisitor.Traversal.PRUNE;
            }
        });
    }

    /**
     * All type parameters in the given type are substituted according to the given substitutions mapping.
     * While doing so, inserted tuple literals are widened when substituted into a generic class.
     * TODO: PY-90853
     */
    public static @Nullable PyType substitute(@Nullable PyType type,
                                              GenericSubstitutions substitutions,
                                              TypeEvalContext context) {
        return PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(context) {
            // Type variables currently being expanded along the active substitution path. A substitution map can be
            // mutually recursive (e.g. {T: S, S: T}) or self-referential (e.g. {T: list[T]}), and the transformations
            // below (toClass/invert/deref) create fresh-but-equal PyTypeVarType instances, so PyCloningTypeVisitor's
            // identity-based cycle guard never trips on them. This equality-based, path-scoped set breaks such cycles
            // to avoid a StackOverflowError.
            private final Set<PyTypeVarType> typeVarsBeingSubstituted = new HashSet<>();

            private List<@Nullable PyType> flattenUnpackedTuple(@Nullable PyType type) {
                if (type instanceof PyUnpackedTupleType unpackedTupleType && !unpackedTupleType.isUnbound()) {
                    return unpackedTupleType.getElementTypes();
                }
                return Collections.singletonList(type);
            }

            @Override
            public PyType visitPyUnpackedTupleType(PyUnpackedTupleType unpackedTupleType) {
                List<@Nullable PyType> elementTypes = new ArrayList<>();
                for (PyType it : unpackedTupleType.getElementTypes()) {
                    elementTypes.addAll(flattenUnpackedTuple(clone(it)));
                }
                return new PyUnpackedTupleTypeImpl(elementTypes, unpackedTupleType.isUnbound());
            }

            @Override
            public @Nullable PyType visitPyTypeVarTupleType(PyTypeVarTupleType typeVarTupleType) {
                if (!substitutions.getTypeVarTuples().containsKey(typeVarTupleType)) {
                    return typeVarTupleType;
                }
                PyPositionalVariadicType substitution = substitutions.getTypeVarTuples().get(typeVarTupleType);
                if (!typeVarTupleType.equals(substitution) && hasGenerics(substitution, context)) {
                    return clone(substitution);
                }
                // TODO This should happen in getSubstitutionsWithUnresolvedReturnGenerics, but it won't work for inherited constructors
                //   as in testVariadicGenericCheckTypeAliasesRedundantParameter, investigate why
                // Replace unknown TypeVarTuples by *tuple[Any, ...] instead of plain Any
                return substitution != null ? substitution : PyUnpackedTupleTypeImpl.UNSPECIFIED;
            }

            @Override
            public @Nullable PyType visitPyTypeVarType(PyTypeVarType typeVarType) {
                // Normalize to the instance form so that the `type[T]` (definition) and `T` (instance) variants produced by
                // the substitution transformations below map to the same cycle key.
                PyTypeVarType cycleKey = typeVarType.isDefinition() ? typeVarType.toInstance() : typeVarType;
                if (!typeVarsBeingSubstituted.add(cycleKey)) {
                    // Reached this type variable again while still expanding it: the substitution map is cyclic.
                    // Stop here and leave it unsubstituted instead of recursing forever (PyCloningTypeVisitor's
                    // identity-based guard can't catch this because each step is a fresh-but-equal instance).
                    return typeVarType;
                }
                try {
                    // Both mappings of kind {T: T2} (and no mapping for T2) and {T: T} mean the substitution process should stop for T.
                    // The first one occurs in the situations like
                    //
                    // def f(x: T1) -> T1: ...
                    // def g(y: T2): f(y)
                    //
                    // when we're inferring the return type of "g".
                    // The second is a plug for type hints like
                    //
                    // def g() -> Callable[[T], T]
                    //
                    // where we want to prevent replacing T with Any, even though it cannot be inferred at a call site.
                    if (!substitutions.getTypeVars().containsKey(typeVarType) && !substitutions.getTypeVars().containsKey(invert(typeVarType))) {
                        PyType substitution = ContainerUtil.find(substitutions.getTypeVars().keySet(), typeVarType2 ->
                            typeVarType2.getDeclarationElement() != null &&
                                (typeVarType.getScopeOwner() == null || (typeVarType2.getScopeOwner() == typeVarType.getScopeOwner()))
                                && typeVarType2.getDeclarationElement().equals(typeVarType.getDeclarationElement())
                        );
                        if (substitution == null) {
                            substitution = PyAnyType.getUnknown();
                        }

                        if (!PyTypeUtilKt.isUnknown(substitution)) {
                            return clone(substitution);
                        }
                        return typeVarType;
                    }
                    Ref<@Nullable PyType> substitutionRef = substitutions.getTypeVars().get(typeVarType);
                    PyType substitution = PyTypeUtil.derefOrUnknown(substitutionRef);
                    if (substitutionRef == null) {
                        PyInstantiableType<?> invertedTypeVar = invert(typeVarType);
                        Ref<@Nullable PyType> invertedRef = substitutions.getTypeVars().get(invertedTypeVar);
                        PyInstantiableType<?> invertedSubstitution =
                            invertedRef != null && invertedRef.get() instanceof PyInstantiableType<?> t ? t : null;
                        if (invertedSubstitution != null && !PyTypeUtilKt.isUnknown(invertedSubstitution)) {
                            substitution = invert(invertedSubstitution);
                        }
                    }
                    if (substitution instanceof PyTypeVarType substitutionTypeVar) {
                        PyTypeVarType sameScopeSubstitution = ContainerUtil.find(substitutions.getTypeVars().keySet(), typeVarType2 ->
                            (!typeVarType2.equals(substitutionTypeVar)) && typeVarType2.getDeclarationElement() != null &&
                                typeVarType2.getDeclarationElement().equals(substitutionTypeVar.getDeclarationElement())
                                && typeVarType2.getScopeOwner() == substitutionTypeVar.getScopeOwner()
                        );

                        if (sameScopeSubstitution != null && substitutionTypeVar.getDefaultType() != null) {
                            return clone(sameScopeSubstitution);
                        }
                    }
                    // Keep the class-object form when the type variable stands for `type[T]`, so that e.g.
                    // `Alias = type[T]` parameterized with `int` yields `type[int]`, not `int` (PY-60614).
                    if (typeVarType.isDefinition() && substitution instanceof PyInstantiableType<?> instantiableType &&
                        !instantiableType.isDefinition()) {
                        substitution = instantiableType.toClass();
                    }
                    // TODO remove !typeVar.equals(substitution) part, it's necessary due to the logic in unifyReceiverWithParamSpecs
                    if ((!typeVarType.equals(substitution)) && !(substitution instanceof PySelfType) && hasGenerics(substitution, context)) {
                        return clone(substitution);
                    }
                    return substitution;
                }
                finally {
                    typeVarsBeingSubstituted.remove(cycleKey);
                }
            }

            @Override
            public @Nullable PyType visitPyParamSpecType(PyParamSpecType paramSpecType) {
                if (!substitutions.getParamSpecs().containsKey(paramSpecType)) {
                    PyParamSpecType sameScopeSubstitution = ContainerUtil.find(substitutions.getParamSpecs().keySet(), typeVarType ->
                        typeVarType.getDeclarationElement() != null && typeVarType.getDeclarationElement().equals(paramSpecType.getDeclarationElement())
                            && (paramSpecType.getScopeOwner() == null || typeVarType.getScopeOwner() == paramSpecType.getScopeOwner())
                    );

                    if (sameScopeSubstitution != null) {
                        return clone(sameScopeSubstitution);
                    }
                    return paramSpecType;
                }
                PyCallableParameterVariadicType substitution = substitutions.getParamSpecs().get(paramSpecType);
                if (substitution != null && (!substitution.equals(paramSpecType)) && hasGenerics(substitution, context)) {
                    return clone(substitution);
                }
                // TODO For ParamSpecs, replace Any with (*args: Any, **kwargs: Any) as it's a logical "wildcard" for this kind of type parameter
                return substitution != null ? substitution : PyAnyType.getUnknown();
            }

            @Override
            public PyType visitPySelfType(PySelfType selfType) {
                PyInstantiableType<?> qType = substitutions.getSelfType();
                if (qType == null) {
                    return selfType;
                }
                PyClassType selfScopeClassType = selfType.getScopeClassType();
                // An enum member has a value-refined literal type (`Literal[E.a]`), but `Self` denotes the enum class itself
                PyInstantiableType<?> normalizedQType = qType instanceof PyLiteralType literalType && literalType.getEnumMemberName() != null
                    ? new PyClassTypeImpl(literalType.getPyClass(), false)
                    : qType;

                PyType result = selfScopeClassType.isDefinition() ? normalizedQType.toClass() : normalizedQType.toInstance();

                // If qualifier type does not match Self's scope class, Self was probably inferred from a different context
                // (e.g. protocol matching for a parameter type) and should be preserved as-is.
                return match(selfScopeClassType, result, context) ? result : selfType;
            }

            @Override
            public @Nullable PyType visitPyClassType(PyClassType classType) {
                if (!classType.isParameterized()) {
                    return super.visitPyClassType(classType);
                }
                PyCollectionTypeImpl clonedClassType = new PyCollectionTypeImpl(
                    classType.getPyClass(), classType.isDefinition(),
                    substituteTypeArguments(classType.getTypeArguments())
                );
                return classType instanceof PyClassTypeImpl classTypeImpl ? classTypeImpl.withUserDataCopy(clonedClassType) : clonedClassType;
            }

            private List<@Nullable PyType> substituteTypeArguments(List<? extends @Nullable PyType> typeArguments) {
                List<@Nullable PyType> result = new ArrayList<>();
                for (PyType typeArg : typeArguments) {
                    result.addAll(flattenUnpackedTuple(clone(typeArg)));
                }
                return result;
            }

            /**
             * The items are substituted through {@link #substitute} rather than through this visitor, so that the
             * parameterized TypedDict holds the substitutions it needs rather than the traversal that produced it, and each
             * item is substituted on a traversal of its own once something asks for it.
             */
            @Override
            public PyType visitPyTypedDictType(PyTypedDictType typedDictType) {
                List<@Nullable PyType> typeArgumentsOrDeclaredParameters = typedDictType.getTypeArgumentsOrDeclaredParameters();
                return new PyTypedDictType(
                    typedDictType.getName(),
                    evalContext -> {
                        Map<String, PyTypedDictType.FieldTypeAndTotality> fields = new LinkedHashMap<>();
                        for (Map.Entry<String, PyTypedDictType.FieldTypeAndTotality> entry : typedDictType.fields(evalContext).entrySet()) {
                            PyTypedDictType.FieldTypeAndTotality field = entry.getValue();
                            fields.put(entry.getKey(), new PyTypedDictType.FieldTypeAndTotality(
                                field.getValue(),
                                substitute(field.getType(), substitutions, evalContext),
                                field.getQualifiers()
                            ));
                        }
                        return fields;
                    },
                    typedDictType.getPyClass(),
                    typedDictType.isDefinition(),
                    typedDictType.getDeclarationElement(),
                    typedDictType.isClosed(),
                    evalContext -> {
                        PyTypedDictType.FieldTypeAndTotality extraItems = typedDictType.extraItems(evalContext);
                        return new PyTypedDictType.FieldTypeAndTotality(
                            extraItems.getValue(),
                            substitute(extraItems.getType(), substitutions, evalContext),
                            extraItems.getQualifiers()
                        );
                    },
                    typedDictType.getDeclaredTypeParameters(),
                    substituteTypeArguments(typeArgumentsOrDeclaredParameters != null ? typeArgumentsOrDeclaredParameters : List.of())
                );
            }

            @Override
            public PyType visitPyNamedTupleType(PyNamedTupleType namedTupleType) {
                return namedTupleType.mapTypes(it -> clone(it), it -> substituteTypeArguments(it));
            }

            @Override
            public PyType visitPyTupleType(PyTupleType tupleType) {
                PyClass tupleClass = tupleType.getPyClass();
                List<@Nullable PyType> oldElementTypes = tupleType.isHomogeneous()
                    ? Collections.singletonList(tupleType.getIteratedItemType())
                    : tupleType.getElementTypes();
                List<@Nullable PyType> newElementTypes = new ArrayList<>();
                for (PyType it : oldElementTypes) {
                    newElementTypes.addAll(flattenUnpackedTuple(clone(it)));
                }
                return new PyTupleType(
                    tupleClass,
                    newElementTypes,
                    tupleType.isHomogeneous()
                );
            }

            @Override
            public @Nullable PyType visitPyFunctionType(PyFunctionType functionType) {
                // TODO legacy behavior
                if (hasGenerics(functionType, context)) {
                    return super.visitPyFunctionType(functionType);
                }
                return functionType;
            }

            @Override
            public PyType visitPyCallableType(PyCallableType callableType) {
                PyCallableParameterVariadicType parametersType = callableType.getParametersType(context);
                PyCallableParameterVariadicType substitutedParams = null;
                if (parametersType != null) {
                    PyType it = clone(parametersType);
                    if (it instanceof PyCallableParameterVariadicType variadicType) {
                        substitutedParams = variadicType;
                    }
                    else if (PyTypeUtilKt.isUnknown(it)) {
                        substitutedParams = null;
                    }
                    else {
                        throw new IllegalStateException("Unexpected type: " + it.getClass().getSimpleName());
                    }
                }

                return new PyCallableTypeImpl(
                    callableType.getTypeParameters(context),
                    substitutedParams,
                    clone(callableType.getReturnType(context)),
                    callableType.getCallable(),
                    callableType.getModifier()
                );
            }

            @Override
            public @Nullable PyType visitPyOverloadType(PyOverloadType overloadType) {
                return PyOverloadTypeKt.map(overloadType, it -> (PyCallableType) visitPyCallableType(it));
            }

            @Override
            public PyType visitPyCallableParameterListType(PyCallableParameterListType callableParameterListType) {
                List<PyCallableParameter> parameters = callableParameterListType.getParameters();

                boolean isSelfArgsKwargs = ParamHelper.isSelfArgsKwargsSignature(parameters);
                boolean isArgsKwargs = ParamHelper.isArgsKwargsSignature(parameters);
                // substitutes (*args: P.args, **kwargs: P.kwargs) with actual signature
                if (isSelfArgsKwargs || isArgsKwargs) {
                    PyType kwargsType = ContainerUtil.getLastItem(parameters).getType(context);
                    PyCallableParameterVariadicType substituted = substitutions.getParamSpecs().get(kwargsType);

                    if (substituted instanceof PyCallableParameterListType substitutedList) {
                        List<PyCallableParameter> newParams = substitutedList.getParameters();
                        if (isSelfArgsKwargs) {
                            newParams = ContainerUtil.prepend(newParams, parameters.get(0));
                        }
                        return new PyCallableParameterListTypeImpl(newParams);
                    }
                }

                List<PyCallableParameter> substitutedParams = new ArrayList<>();
                for (PyCallableParameter param : parameters) {
                    PyParameter paramPsi = param.getParameter();
                    for (PyType paramSubType : flattenUnpackedTuple(clone(param.getType(context)))) {
                        if (paramPsi != null) {
                            substitutedParams.add(PyCallableParameterImpl.psi(paramPsi, paramSubType));
                        }
                        else {
                            substitutedParams.add(new PyCallableParameterImpl(
                                param.getName(),
                                Ref.create(paramSubType),
                                param.getDefaultValue(),
                                param.getDefaultValueText(),
                                param.getParameter(),
                                param.isPositionalContainer(),
                                param.isKeywordContainer(),
                                param.isSelf(),
                                param.isKeywordOnlySeparator(),
                                param.isPositionOnlySeparator(),
                                param.getDeclarationElement()
                            ));
                        }
                    }
                }

                return new PyCallableParameterListTypeImpl(substitutedParams);
            }

            @Override
            public @Nullable PyType visitPyConcatenateType(PyConcatenateType concatenateType) {
                List<@Nullable PyType> firstParamTypeSubs = new ArrayList<>();
                for (PyType it : concatenateType.getFirstTypes()) {
                    firstParamTypeSubs.addAll(flattenUnpackedTuple(clone(it)));
                }
                if (concatenateType.getParamSpec() == null) {
                    return PyAnyType.getUnknown();
                }
                PyType clonedParamSpec = clone(concatenateType.getParamSpec());
                PyCallableParameterVariadicType paramSpecSubs;
                if (clonedParamSpec instanceof PyCallableParameterVariadicType variadicType) {
                    paramSpecSubs = variadicType;
                }
                else if (PyTypeUtilKt.isUnknown(clonedParamSpec)) {
                    paramSpecSubs = null;
                }
                else {
                    throw new IllegalStateException("Unexpected type for paramSpec: " + clonedParamSpec.getClass().getSimpleName());
                }

                if (paramSpecSubs instanceof PyCallableParameterListType parameterListType) {
                    List<PyCallableParameter> newParameters = new ArrayList<>(ContainerUtil.map(firstParamTypeSubs, PyCallableParameterImpl::nonPsi));
                    newParameters.addAll(parameterListType.getParameters());
                    return new PyCallableParameterListTypeImpl(newParameters);
                }
                else if (paramSpecSubs instanceof PyParamSpecType paramSpecType) {
                    return new PyConcatenateType(firstParamTypeSubs, paramSpecType);
                }
                else if (paramSpecSubs instanceof PyConcatenateType concatenateSubs) {
                    List<@Nullable PyType> firstTypes = new ArrayList<>(firstParamTypeSubs);
                    firstTypes.addAll(concatenateSubs.getFirstTypes());
                    return new PyConcatenateType(
                        firstTypes,
                        concatenateSubs.getParamSpec()
                    );
                }
                else {
                    return PyAnyType.getUnknown();
                }
            }
        });
    }

    public static @Nullable GenericSubstitutions unifyGenericCall(Map<PyCallableArgument, PyCallableParameter> arguments,
                                                                  TypeEvalContext context) {
        GenericSubstitutions substitutions = new GenericSubstitutions();
        return matchArguments(arguments, substitutions, context) ? substitutions : null;
    }

    private static boolean matchArguments(Map<PyCallableArgument, PyCallableParameter> arguments,
                                          GenericSubstitutions substitutions,
                                          TypeEvalContext context) {
        return matchArguments(arguments, substitutions, context, Collections.emptySet());
    }

    private static boolean matchArguments(Map<PyCallableArgument, PyCallableParameter> arguments,
                                          GenericSubstitutions substitutions,
                                          TypeEvalContext context,
                                          Set<PyParamSpecType> expandedParamSpecs) {
        for (Map.Entry<PyCallableArgument, PyCallableParameter> entry : PyCallExpressionHelper.getRegularMappedParameters(arguments).entrySet()) {
            PyCallableArgument argument = entry.getKey();
            PyCallableParameter parameter = entry.getValue();
            PyType expectedType = parameter.getArgumentType(context);
            PyExpression argumentExpression = argument.getExpression();
            PyType promotedToLiteral = argumentExpression != null
                ? PyLiteralType.promoteToLiteral(argumentExpression, expectedType, context, substitutions)
                : null;
            PyType actualType = promotedToLiteral != null && !PyTypeUtilKt.isUnknown(promotedToLiteral) ? promotedToLiteral : argument.getType(context);
            boolean matchedByTypes = match(expectedType, actualType, context, substitutions);
            if (!matchedByTypes) {
                return false;
            }
        }
        PyParamSpecType paramSpec = null;
        List<PyCallableArgument> forwardedArguments = new ArrayList<>();
        List<Pair<@Nullable PyCallableParameter, List<PyCallableArgument>>> containers = List.of(
            Pair.create(PyCallExpressionHelper.getMappedPositionalContainer(arguments),
                        PyCallExpressionHelper.getArgumentsMappedToPositionalContainer(arguments)),
            Pair.create(PyCallExpressionHelper.getMappedKeywordContainer(arguments),
                        PyCallExpressionHelper.getArgumentsMappedToKeywordContainer(arguments))
        );
        for (Pair<@Nullable PyCallableParameter, List<PyCallableArgument>> pair : containers) {
            PyCallableParameter container = pair.getFirst();
            List<PyCallableArgument> containerArguments = pair.getSecond();
            PyType containerType = container != null ? container.getArgumentType(context) : null;
            if (containerType instanceof PyParamSpecType containerParamSpec) {
                paramSpec = containerParamSpec;
                forwardedArguments.addAll(containerArguments);
            }
            else if (!matchContainer(container, containerArguments, substitutions, context)) {
                return false;
            }
        }
        return paramSpec == null || matchParamSpecArguments(paramSpec, forwardedArguments, substitutions, context, expandedParamSpecs);
    }

    /**
     * Matches the arguments for {@code *args: P.args} and {@code **kwargs: P.kwargs} against the parameters of {@code P}.
     * The other arguments of the call solve {@code P}. If they do not solve it, nothing is checked.
     * The match fails if an argument has no parameter of {@code P}, or a required parameter of {@code P} has no argument.
     * {@code expandedParamSpecs} stops the recursion when the parameters of {@code P} refer to {@code P} again.
     */
    private static boolean matchParamSpecArguments(PyParamSpecType paramSpec,
                                                   List<PyCallableArgument> arguments,
                                                   GenericSubstitutions substitutions,
                                                   TypeEvalContext context,
                                                   Set<PyParamSpecType> expandedParamSpecs) {
        if (expandedParamSpecs.contains(paramSpec)) {
            return true;
        }
        if (!(substitutions.getParamSpecs().get(paramSpec) instanceof PyCallableParameterListType parameters)) {
            return true;
        }
        var mapping = PyCallExpressionHelper.analyzeArguments(arguments, parameters, context);
        Set<PyParamSpecType> newExpandedParamSpecs = new HashSet<>(expandedParamSpecs);
        newExpandedParamSpecs.add(paramSpec);
        return mapping.getUnmappedArguments().isEmpty() &&
            mapping.getUnmappedParameters().isEmpty() &&
            matchArguments(mapping.getMappedParameters(), substitutions, context, newExpandedParamSpecs);
    }

    private static boolean matchContainer(@Nullable PyCallableParameter container,
                                          List<PyCallableArgument> arguments,
                                          GenericSubstitutions substitutions,
                                          TypeEvalContext context) {
        if (container == null) {
            return true;
        }
        List<@Nullable PyType> actualArgumentTypes = ContainerUtil.map(arguments, it -> it.getType(context));
        PyType expectedArgumentType = container.getArgumentType(context);
        if (container.isPositionalContainer() && expectedArgumentType instanceof PyPositionalVariadicType positionalVariadicType) {
            return match(
                positionalVariadicType, PyUnpackedTupleTypeImpl.create(actualArgumentTypes),
                new MatchContext(context, substitutions, false)
            );
        }
        return match(expectedArgumentType, PyUnionType.unionOrUnknown(actualArgumentTypes), context, substitutions);
    }

    /**
     * @deprecated Use PyTypeChecker.unifyReceiver(PyType, TypeEvalContext)
     */
    @Deprecated
    public static GenericSubstitutions unifyReceiver(@Nullable PyExpression receiver, TypeEvalContext context) {
        if (receiver != null) {
            PyType receiverType = context.getType(receiver);
            return unifyReceiver(receiverType, context);
        }
        return new GenericSubstitutions();
    }

    public static GenericSubstitutions unifyReceiver(@Nullable PyType receiverType, TypeEvalContext context) {
        if (receiverType instanceof PyClassType classType) {
            GenericSubstitutions substitutions = collectTypeSubstitutions(classType, context);
            substitutions.setSelfType(classType);
            substitutions.setFrozenTypeVars(new HashSet<>(substitutions.getTypeVars().keySet()), KeyImpl.INSTANCE);
            return substitutions;
        }
        return new GenericSubstitutions();
    }

    private static boolean matchClasses(@Nullable PyClass superClass, @Nullable PyClass subClass, TypeEvalContext context) {
        if (superClass == null || subClass == null ||
            subClass.isSubclass(superClass, context) ||
            PyABCUtil.isSubclass(subClass, superClass, context) ||
            isStrUnicodeMatch(subClass, superClass) ||
            isBytearrayBytesStringMatch(subClass, superClass) ||
            PyUtil.hasUnresolvedAncestors(subClass, context)
        ) {
            return true;
        }
        else {
            String superName = superClass.getName();
            return superName != null && superName.equals(subClass.getName());
        }
    }

    private static boolean isStrUnicodeMatch(PyClass subClass, PyClass superClass) {
        // TODO: Check for subclasses as well
        return PyNames.TYPE_STR.equals(subClass.getName()) && PyNames.TYPE_UNICODE.equals(superClass.getName());
    }

    private static boolean isBytearrayBytesStringMatch(PyClass subClass, PyClass superClass) {
        if (!PyNames.TYPE_BYTEARRAY.equals(subClass.getName())) {
            return false;
        }

        var subClassFile = subClass.getContainingFile();

        boolean isPy2 = subClassFile instanceof PyiFile
            ? PythonRuntimeService.getInstance().getLanguageLevelForSdk(MainPythonSdkKt.pythonSdkIfReady(subClassFile)).isPython2()
            : LanguageLevel.forElement(subClass).isPython2();

        String superClassName = superClass.getName();
        return isPy2 && PyNames.TYPE_STR.equals(superClassName) || !isPy2 && PyNames.TYPE_BYTES.equals(superClassName);
    }

    public static @Nullable Boolean isCallable(@Nullable PyType type) {
        PyAnyType.validate(type);
        if (type == null || type instanceof PyAnyType) {
            return null;
        }
        if (type instanceof PyUnionType unionType) {
            return isCallable(unionType);
        }
        if (type instanceof PyUnsafeUnionType unsafeUnionType) {
            return isCallable(unsafeUnionType);
        }
        if (type instanceof PyIntersectionType intersectionType) {
            return isCallable(intersectionType);
        }
        if (type instanceof PyOverloadType) {
            return true;
        }
        if (type instanceof PyCallableType callableType) {
            return callableType.isCallable();
        }
        if (type instanceof PyStructuralType structuralType && structuralType.isInferredFromUsages()) {
            return true;
        }
        if (type instanceof PyTypeVarType typeVarType) {
            return typeVarType.isDefinition() ? Boolean.TRUE : isCallable(typeVarType.getEffectiveBound());
        }
        return false;
    }

    /**
     * If any are not callable -- it is not callable.
     * If any are unknown -- it is unknown.
     * It is true otherwise.
     */
    private static @Nullable Boolean isCallable(PyUnionType type) {
        return PyUnionType.isStrictSemanticsEnabled()
            ? allCallable(type.getMembers())
            : anyCallable(type.getMembers());
    }

    /**
     * If at least one is callable -- it is callable.
     * If at least one is unknown -- it is unknown.
     * It is false otherwise.
     */
    private static @Nullable Boolean isCallable(PyUnsafeUnionType type) {
        return anyCallable(type.getMembers());
    }

    /**
     * A value typed as an intersection is callable if at least one member is.
     * This is the same disjunctive rule as an unsafe union.
     */
    private static @Nullable Boolean isCallable(PyIntersectionType type) {
        return anyCallable(type.getMembers());
    }

    private static @Nullable Boolean anyCallable(Iterable<? extends @Nullable PyType> types) {
        boolean seenNull = false;
        for (PyType member : types) {
            Boolean callable = isCallable(member);
            if (callable == null) {
                seenNull = true;
            }
            else if (callable) {
                return true;
            }
        }
        return seenNull ? null : Boolean.FALSE;
    }

    private static @Nullable Boolean allCallable(Iterable<? extends @Nullable PyType> types) {
        boolean seenNull = false;
        for (PyType member : types) {
            Boolean callable = isCallable(member);
            if (callable == null) {
                seenNull = true;
            }
            else if (!callable) {
                return false;
            }
        }
        return seenNull ? null : Boolean.TRUE;
    }

    public static boolean definesGetAttr(PyFile file, TypeEvalContext context) {
        PyType type = context.getType(file);
        if (type == null) {
            return false;
        }
        return resolveTypeMember(type, PyNames.GETATTR, context) != null;
    }

    public static boolean overridesGetAttr(PyClassType classType, TypeEvalContext context) {
        return overridesGetAttr(classType, context, null);
    }

    public static boolean overridesGetAttr(PyClassType classType, TypeEvalContext context, @Nullable String name) {
        return getGetAttrType(classType, context, name) != null;
    }

    private static @Nullable Ref<@Nullable PyType> getGetAttrType(PyClassType classType, TypeEvalContext context, @Nullable String name) {
        PyClass pyClass = classType.getPyClass();
        PyType nameArg = name != null ? PyLiteralType.stringLiteral(pyClass, name) : PyLiteralStringType.create(pyClass);
        List<PyCallableArgument> arguments = List.of(new PyCallableArgument(nameArg));
        Ref<@Nullable PyType> result = PyCallExpressionHelper.getSpecialMethodCallType(classType, PyNames.GETATTR, arguments, context, true, false);
        if (result != null) {
            return result;
        }
        // For some reason __getattribute__ is declared directly on `builtins.object` returning `Any`
        return PyCallExpressionHelper.getSpecialMethodCallType(classType, PyNames.GETATTRIBUTE, arguments, context, true, true);
    }

    private static @Nullable PsiElement resolveTypeMember(PyType type, String name, TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
        return results != null && !results.isEmpty() ? results.get(0).getElement() : null;
    }

    public static @Nullable PyType getTargetTypeFromTupleAssignment(PyExpression target,
                                                                    PySequenceExpression parentTupleOrList,
                                                                    PyTupleType assignedTupleType,
                                                                    TypeEvalContext context) {
        int count = assignedTupleType.getElementCount();
        PyExpression[] elements = parentTupleOrList.getElements();

        // Handle starred target (e.g. `head, *tail = (1, "b")`)
        int starElementIndex = -1;
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof PyStarExpression) {
                starElementIndex = i;
                break;
            }
        }
        if (starElementIndex >= 0) {
            PyStarExpression starElement = (PyStarExpression) elements[starElementIndex];

            if (assignedTupleType.isHomogeneous()) {
                PyType elementType = PyLiteralType.upcastLiteralToClass(assignedTupleType.getIteratedItemType());
                if (Objects.equals(starElement.getExpression(), target)) {
                    PyClass listClass = PyBuiltinCache.getInstance(target).getClass("list");
                    if (listClass == null) {
                        return PyAnyType.getUnknown();
                    }
                    return new PyCollectionTypeImpl(listClass, false, Collections.singletonList(elementType));
                }
                return ArrayUtil.contains(target, elements) ? elementType : null;
            }

            int nonStarCount = elements.length - 1;
            if (count < nonStarCount) {
                return null;
            }

            if (Objects.equals(starElement.getExpression(), target)) {
                // Star target collects the middle slice of the assigned tuple as a list
                int afterCount = nonStarCount - starElementIndex;
                List<@Nullable PyType> sliceTypes = new ArrayList<>();
                for (int it = starElementIndex; it < count - afterCount; it++) {
                    sliceTypes.add(assignedTupleType.getElementType(it));
                }
                // Widen literal types: the slice becomes a `list`, whose element type should not be a literal
                PyType elementType = PyLiteralType.upcastLiteralToClass(PyUnionType.unionOrUnknown(sliceTypes));
                PyClass listClass = PyBuiltinCache.getInstance(target).getClass("list");
                if (listClass == null) {
                    return PyAnyType.getUnknown();
                }
                return new PyCollectionTypeImpl(listClass, false, Collections.singletonList(elementType));
            }

            // Non-star target: map each LHS position to the corresponding assigned-tuple index,
            // accounting for the star element that consumes the middle slice, and recurse into
            // nested sequence targets (e.g. `(x,)` in `((x,), *_) = ...`).
            for (int i = 0; i < elements.length; i++) {
                if (i == starElementIndex) {
                    continue;
                }
                int effectiveIndex = i < starElementIndex ? i : count - (elements.length - i);
                if (effectiveIndex < 0 || effectiveIndex >= count) {
                    continue;
                }
                PyExpression element = PyPsiUtils.flattenParens(elements[i]);
                if (Objects.equals(element, target)) {
                    return assignedTupleType.getElementType(effectiveIndex);
                }
                if ((element instanceof PyTupleExpression || element instanceof PyListLiteralExpression)
                    && element.getTextRange().contains(target.getTextRange())) {
                    // The target is nested inside this element; unpack the corresponding item type into it. Delegating to the
                    // iterable-unpacking entry point handles a nested homogeneous iterable (e.g. a `list` inside the tuple),
                    // not just a nested tuple.
                    PyType elementType = assignedTupleType.getElementType(effectiveIndex);
                    if (elementType == null) {
                        return null;
                    }
                    return PyTargetExpressionImpl.getTargetTypeFromIterableUnpacking(target, (PySequenceExpression) element, null, elementType, context);
                }
            }
            return null;
        }

        if (elements.length == count || assignedTupleType.isHomogeneous()) {
            int index = ArrayUtil.indexOf(elements, target);
            if (index >= 0) {
                return assignedTupleType.getElementType(index);
            }
            for (int i = 0; i < count; i++) {
                PyExpression element = PyPsiUtils.flattenParens(elements[i]);
                if ((element instanceof PyTupleExpression || element instanceof PyListLiteralExpression)
                    && element.getTextRange().contains(target.getTextRange())) {
                    // The target is nested inside this element; unpack the corresponding item type into it. Delegating to the
                    // iterable-unpacking entry point handles a nested homogeneous iterable (e.g. a `list` inside the tuple),
                    // not just a nested tuple.
                    PyType elementType = assignedTupleType.getElementType(i);
                    if (elementType == null) {
                        return null;
                    }
                    return PyTargetExpressionImpl.getTargetTypeFromIterableUnpacking(target, (PySequenceExpression) element, null, elementType, context);
                }
            }
        }
        return null;
    }

    /**
     * Populates an existing generic type with given actual type parameters in their original order.
     *
     * @return a parameterized version of the original type or {@code null} if it cannot be meaningfully parameterized.
     */
    public static @Nullable PyType parameterizeType(PyType genericType,
                                                    List<? extends @Nullable PyType> actualTypeParams,
                                                    TypeEvalContext context) {
        Generics typeParams = collectGenerics(genericType, context);
        if (!typeParams.isEmpty()) {
            List<PyType> expectedTypeParams = new ArrayList<>(new LinkedHashSet<>(typeParams.getAllTypeParameters()));
            GenericSubstitutions substitutions = mapTypeParametersToSubstitutions(
                expectedTypeParams,
                actualTypeParams,
                PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
                PyTypeParameterMapping.Option.USE_DEFAULTS
            );
            if (substitutions == null) {
                return null;
            }
            return substitute(genericType, substitutions, context);
        }
        else if (genericType instanceof PyClassType classType && classType.isParameterized()) {
            return genericType;
        }
        else if (genericType instanceof PyClassType classType) {
            PyClass cls = classType.getPyClass();
            return new PyCollectionTypeImpl(cls, false, actualTypeParams);
        }
        return null;
    }

    public static @Nullable GenericSubstitutions mapTypeParametersToSubstitutions(List<? extends @Nullable PyType> expectedTypes,
                                                                                  List<? extends @Nullable PyType> actualTypes,
                                                                                  PyTypeParameterMapping.Option... options) {
        return mapTypeParametersToSubstitutions(new GenericSubstitutions(), expectedTypes, actualTypes, options);
    }

    private static @Nullable GenericSubstitutions mapTypeParametersToSubstitutions(GenericSubstitutions substitutions,
                                                                                   List<? extends @Nullable PyType> expectedTypes,
                                                                                   List<? extends @Nullable PyType> actualTypes,
                                                                                   PyTypeParameterMapping.Option... options) {
        PyTypeParameterMapping mapping = PyTypeParameterMapping.mapByShape(expectedTypes, actualTypes, options);
        if (mapping == null) {
            return null;
        }
        for (Couple<PyType> pair : mapping.getMappedTypes()) {
            PyType first = pair.getFirst();
            PyType second = pair.getSecond();
            if (first instanceof PyTypeVarType typeVarType) {
                substitutions.putTypeVar(typeVarType, Ref.create(second), KeyImpl.INSTANCE);
            }
            else if (first instanceof PyTypeVarTupleType typeVarTupleType) {
                substitutions.putTypeVarTuple(typeVarTupleType, second instanceof PyPositionalVariadicType v ? v : null, KeyImpl.INSTANCE);
            }
            else if (first instanceof PyParamSpecType paramSpecType) {
                substitutions.putParamSpec(paramSpecType, second instanceof PyCallableParameterVariadicType v ? v : null, KeyImpl.INSTANCE);
            }
        }
        return substitutions;
    }

    public static @Nullable PyType convertToType(@Nullable PyType type, PyClassType superType, TypeEvalContext context) {
        MatchContext matchContext = new MatchContext(context, new GenericSubstitutions(), false);
        Optional<Boolean> matched = match(superType, type, matchContext);
        if (matched.orElse(false)) {
            // There is a tricky problem with handling type parameter binds to Any. Namely, during matching list[Any] to Iterable[T@Iterable],
            // we don't keep the bind T@Iterable -> Any (see the implementation of `match(PyTypeVarType, PyType, MatchContext)`).
            // As a workaround, until we migrate to type checking with CSP, we consider that
            // all parameter of the super types should be bound, and if they aren't, we fall back them to Any.
            Generics resolvableTypeParams = collectGenerics(superType, context);
            GenericSubstitutions substitutions =
                substituteUnboundTypeVarsWithDefaultOrAny(superType, resolvableTypeParams, matchContext.mySubstitutions, context);
            return substitute(superType, substitutions, context);
        }
        return null;
    }

    /**
     * Computes the type produced when iterating over an instance of {@code classType}, or {@code null} if it is not iterable.
     * <p>
     * The first type argument cannot be assumed to be the iterated element: a class may parameterize its {@code Iterable}
     * ancestor with a concrete type (e.g. {@code class A[T](list[str])} iterates over {@code str}, not {@code T}) or remap its type
     * parameters (e.g. a mapping iterates over its key). The element is therefore resolved by upcasting to the
     * (sync or async) {@code Iterable} protocol, except for the iterable/iterator protocols themselves, whose first type
     * argument is returned directly so that literal type arguments are preserved.
     */
    public static @Nullable PyType getIteratedItemType(PyClassType classType, TypeEvalContext context) {
        List<@Nullable PyType> typeArguments = classType.getTypeArguments();
        // For the (sync/async) iterable and iterator protocols themselves the first type argument *is* the element type.
        // Shortcut them so that a literal type argument (e.g. `Iterator[LiteralString]`) is preserved instead of being
        // widened by upcasting to `Iterable[T]`.
        if (PyTypingTypeProvider.ITERABLE.equals(classType.getClassQName()) ||
            PyTypingTypeProvider.ITERATOR.equals(classType.getClassQName()) ||
            PyTypingTypeProvider.ASYNC_ITERABLE.equals(classType.getClassQName()) ||
            PyTypingTypeProvider.ASYNC_ITERATOR.equals(classType.getClassQName())) {
            return ContainerUtil.getFirstItem(typeArguments);
        }
        PyType iterable = PyTypeUtil.convertToType(classType, PyTypingTypeProvider.ITERABLE, classType.getPyClass(), context);
        if (iterable instanceof PyClassType iterableClass && iterableClass.isParameterized()) {
            return getIteratedItemType(iterableClass, context);
        }
        PyType asyncIterable = PyTypeUtil.convertToType(classType, PyTypingTypeProvider.ASYNC_ITERABLE, classType.getPyClass(), context);
        if (asyncIterable instanceof PyClassType asyncIterableClass && asyncIterableClass.isParameterized()) {
            return getIteratedItemType(asyncIterableClass, context);
        }
        return PyAnyType.getUnknown();
    }

    private static final class MyMatchHelper {
        private final Object myKey;
        private final MatchContext myContext;

        private MyMatchHelper(Object key, MatchContext context) {
            myKey = key;
            myContext = context;
        }

        boolean match(@Nullable PyType expected, @Nullable PyType actual) {
            Optional<Boolean> result = RecursionManager.doPreventingRecursion(
                myKey, false, myContext.reversedSubstitutions
                    ? () -> PyTypeChecker.match(actual, expected, myContext)
                    : () -> PyTypeChecker.match(expected, actual, myContext)
            );
            return result != null && result.orElse(false);
        }
    }

    public static Generics Generics() {
        return new GenericsImpl();
    }

    public sealed interface Generics permits GenericsImpl {
        Set<PyTypeVarType> getTypeVars();

        Set<PyTypeVarTupleType> getTypeVarTuples();

        List<PyTypeParameterType> getAllTypeParameters();

        Set<PyParamSpecType> getParamSpecs();

        @Nullable PySelfType getSelf();

        boolean isEmpty();
    }

    private static final class GenericsImpl implements Generics {
        final Set<PyTypeVarType> typeVars = new LinkedHashSet<>();
        final Set<PyTypeVarTupleType> typeVarTuples = new LinkedHashSet<>();
        final List<PyTypeParameterType> allTypeParameters = new ArrayList<>();
        final Set<PyParamSpecType> paramSpecs = new LinkedHashSet<>();
        @Nullable PySelfType self = null;

        @Override
        public Set<PyTypeVarType> getTypeVars() {
            return typeVars;
        }

        @Override
        public Set<PyTypeVarTupleType> getTypeVarTuples() {
            return typeVarTuples;
        }

        @Override
        public List<PyTypeParameterType> getAllTypeParameters() {
            return allTypeParameters;
        }

        @Override
        public Set<PyParamSpecType> getParamSpecs() {
            return paramSpecs;
        }

        @Override
        public @Nullable PySelfType getSelf() {
            return self;
        }

        @Override
        public boolean isEmpty() {
            return typeVars.isEmpty() && typeVarTuples.isEmpty() && paramSpecs.isEmpty() && self == null;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GenericsImpl other)) {
                return false;
            }
            return typeVars.equals(other.typeVars) &&
                typeVarTuples.equals(other.typeVarTuples) &&
                allTypeParameters.equals(other.allTypeParameters) &&
                paramSpecs.equals(other.paramSpecs) &&
                Objects.equals(self, other.self);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeVars, typeVarTuples, allTypeParameters, paramSpecs, self);
        }

        @Override
        public String toString() {
            return "GenericsImpl(typeVars=" + typeVars + ", typeVarTuples=" + typeVarTuples + ", allTypeParameters=" + allTypeParameters +
                ", paramSpecs=" + paramSpecs + ", self=" + self + ")";
        }
    }

    public static class GenericSubstitutions {
        private final Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> myTypeVars = new LinkedHashMap<>();

        private final Map<PyTypeVarTupleType, @Nullable PyPositionalVariadicType> myTypeVarTuples = new LinkedHashMap<>();

        private final Map<PyParamSpecType, @Nullable PyCallableParameterVariadicType> myParamSpecs = new LinkedHashMap<>();

        // Substitution for `typing.Self`
        private @Nullable PyInstantiableType<?> selfType = null;

        private Set<PyTypeVarType> frozenTypeVars = Collections.emptySet();

        public GenericSubstitutions() {
        }

        public GenericSubstitutions(Map<? extends PyTypeParameterType, ? extends @Nullable PyType> typeParameters) {
            for (Map.Entry<? extends PyTypeParameterType, ? extends @Nullable PyType> entry : typeParameters.entrySet()) {
                PyTypeParameterType key = entry.getKey();
                PyType value = entry.getValue();
                if (key instanceof PyTypeVarType typeVarType) {
                    myTypeVars.put(typeVarType, Ref.create(PyNumericTowerUtil.enrich(value)));
                }
                else if (key instanceof PyTypeVarTupleType typeVarTupleType) {
                    if (value instanceof PyPositionalVariadicType positionalVariadicType) {
                        myTypeVarTuples.put(typeVarTupleType, positionalVariadicType);
                    }
                }
                else if (key instanceof PyParamSpecType paramSpecType) {
                    if (value instanceof PyCallableParameterVariadicType variadicType) {
                        myParamSpecs.put(paramSpecType, variadicType);
                    }
                }
            }
        }

        public GenericSubstitutions(Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> typeVars,
                                    Map<PyTypeVarTupleType, @Nullable PyPositionalVariadicType> typeVarTuples,
                                    Map<PyParamSpecType, @Nullable PyCallableParameterVariadicType> paramSpecs,
                                    @Nullable PyInstantiableType<?> selfType) {
            for (Map.Entry<PyTypeVarType, @Nullable Ref<@Nullable PyType>> entry : typeVars.entrySet()) {
                putTypeVar(entry.getKey(), entry.getValue(), KeyImpl.INSTANCE);
            }
            this.myTypeVarTuples.putAll(typeVarTuples);
            this.myParamSpecs.putAll(paramSpecs);
            this.selfType = selfType;
        }

        public Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> getTypeVars() {
            return Collections.unmodifiableMap(myTypeVars);
        }

        public Map<PyTypeVarTupleType, @Nullable PyPositionalVariadicType> getTypeVarTuples() {
            return Collections.unmodifiableMap(myTypeVarTuples);
        }

        public Map<PyParamSpecType, @Nullable PyCallableParameterVariadicType> getParamSpecs() {
            return Collections.unmodifiableMap(myParamSpecs);
        }

        public @Nullable PyInstantiableType<?> getSelfType() {
            return selfType;
        }

        public void setSelfType(@Nullable PyInstantiableType<?> selfType) {
            this.selfType = selfType;
        }

        public GenericSubstitutions addToCopy(Map<PyTypeParameterType, @Nullable Ref<@Nullable PyType>> typeParameters) {
            Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> typeVars = new HashMap<>();
            Map<PyTypeVarTupleType, PyPositionalVariadicType> typeVarTuples = new HashMap<>();
            Map<PyParamSpecType, PyCallableParameterVariadicType> paramSpecs = new HashMap<>();
            for (Map.Entry<PyTypeParameterType, @Nullable Ref<@Nullable PyType>> entry : typeParameters.entrySet()) {
                PyTypeParameterType key = entry.getKey();
                Ref<@Nullable PyType> value = entry.getValue();
                if (key instanceof PyTypeVarType typeVarType) {
                    typeVars.put(typeVarType, value);
                }
                else if (key instanceof PyTypeVarTupleType typeVarTupleType) {
                    if ((Object) value instanceof PyPositionalVariadicType positionalVariadicType) {
                        typeVarTuples.put(typeVarTupleType, positionalVariadicType);
                    }
                }
                else if (key instanceof PyParamSpecType paramSpecType) {
                    if ((Object) value instanceof PyCallableParameterVariadicType variadicType) {
                        paramSpecs.put(paramSpecType, variadicType);
                    }
                }
            }
            return addToCopy(typeVars, typeVarTuples, paramSpecs);
        }

        public GenericSubstitutions addToCopy(@Nullable Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> typeVars,
                                              @Nullable Map<PyTypeVarTupleType, PyPositionalVariadicType> typeVarTuples,
                                              @Nullable Map<PyParamSpecType, PyCallableParameterVariadicType> paramSpecs) {
            Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> newTypeVars = new LinkedHashMap<>(this.getTypeVars());
            if (typeVars != null) {
                newTypeVars.putAll(typeVars);
            }
            Map<PyTypeVarTupleType, @Nullable PyPositionalVariadicType> newTypeVarTuples = new LinkedHashMap<>(this.getTypeVarTuples());
            if (typeVarTuples != null) {
                newTypeVarTuples.putAll(typeVarTuples);
            }
            Map<PyParamSpecType, @Nullable PyCallableParameterVariadicType> newParamSpecs = new LinkedHashMap<>(this.getParamSpecs());
            if (paramSpecs != null) {
                newParamSpecs.putAll(paramSpecs);
            }
            return new GenericSubstitutions(newTypeVars, newTypeVarTuples, newParamSpecs, selfType);
        }

        public GenericSubstitutions simplify(TypeEvalContext context) {
            Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> simplifiedTypeVars = new LinkedHashMap<>();
            for (Map.Entry<PyTypeVarType, @Nullable Ref<@Nullable PyType>> entry : myTypeVars.entrySet()) {
                Ref<@Nullable PyType> value = entry.getValue();
                simplifiedTypeVars.put(entry.getKey(), Ref.create(substitute(value != null ? value.get() : null, this, context)));
            }
            return new GenericSubstitutions(simplifiedTypeVars, getTypeVarTuples(), getParamSpecs(), selfType);
        }

        public Set<PyTypeVarType> getFrozenTypeVars(@SuppressWarnings("unused") Key key) {
            return frozenTypeVars;
        }

        /**
         * An exact copy, with the frozen type variables.
         */
        public GenericSubstitutions copy(Key key) {
            GenericSubstitutions result = new GenericSubstitutions();
            result.replaceWith(this, key);
            return result;
        }

        /**
         * Replaces all substitutions and the frozen type variables with the ones of {@code other}.
         */
        public void replaceWith(GenericSubstitutions other, @SuppressWarnings("unused") Key key) {
            if (other == this) {
                return;
            }
            myTypeVars.clear();
            myTypeVars.putAll(other.myTypeVars);
            myTypeVarTuples.clear();
            myTypeVarTuples.putAll(other.myTypeVarTuples);
            myParamSpecs.clear();
            myParamSpecs.putAll(other.myParamSpecs);
            selfType = other.selfType;
            frozenTypeVars = other.frozenTypeVars;
        }

        public void setFrozenTypeVars(Set<PyTypeVarType> value, @SuppressWarnings("unused") Key key) {
            frozenTypeVars = value;
        }

        public void putTypeVar(PyTypeVarType typeVar, @Nullable Ref<@Nullable PyType> substitute, Key key) {
            putTypeVar(typeVar, substitute, key, false);
        }

        public void putTypeVar(PyTypeVarType typeVar,
                               @Nullable Ref<@Nullable PyType> substitute,
                               @SuppressWarnings("unused") Key key,
                               boolean ifAbsent) {
            Ref<@Nullable PyType> safeSubstitute = substitute != null ? Ref.create(PyNumericTowerUtil.enrich(PyTypeUtil.derefOrUnknown(substitute))) : null;
            if (ifAbsent) {
                myTypeVars.putIfAbsent(typeVar, safeSubstitute);
            }
            else {
                myTypeVars.put(typeVar, safeSubstitute);
            }
        }

        public void putTypeVarTuple(PyTypeVarTupleType typeVarTuple, @Nullable PyPositionalVariadicType substitute, Key key) {
            putTypeVarTuple(typeVarTuple, substitute, key, false);
        }

        public void putTypeVarTuple(PyTypeVarTupleType typeVarTuple,
                                    @Nullable PyPositionalVariadicType substitute,
                                    @SuppressWarnings("unused") Key key,
                                    boolean ifAbsent) {
            if (ifAbsent) {
                myTypeVarTuples.putIfAbsent(typeVarTuple, substitute);
            }
            else {
                myTypeVarTuples.put(typeVarTuple, substitute);
            }
        }

        public void putParamSpec(PyParamSpecType paramSpec, @Nullable PyCallableParameterVariadicType substitute, Key key) {
            putParamSpec(paramSpec, substitute, key, false);
        }

        public void putParamSpec(PyParamSpecType paramSpec,
                                 @Nullable PyCallableParameterVariadicType substitute,
                                 @SuppressWarnings("unused") Key key,
                                 boolean ifAbsent) {
            if (ifAbsent) {
                myParamSpecs.putIfAbsent(paramSpec, substitute);
            }
            else {
                myParamSpecs.put(paramSpec, substitute);
            }
        }

        @Override
        public String toString() {
            return "GenericSubstitutions(typeVars=" + getTypeVars() + ", typeVarTuples" + getTypeVarTuples() + ", paramSpecs=" + getParamSpecs() +
                ", selfType=" + selfType + ")";
        }
    }

    public static sealed abstract class Key permits KeyImpl {
    }

    private static final class KeyImpl extends Key {
        static final KeyImpl INSTANCE = new KeyImpl();

        private KeyImpl() {
        }
    }

    private static final class MatchContext {
        final TypeEvalContext context;
        final GenericSubstitutions mySubstitutions;
        final boolean reversedSubstitutions;

        /**
         * {@code null} during normal (cheap) matching, so it adds no overhead. Non-null only while
         * {@link #explainMismatch} re-runs the match to collect a breakdown for an already-decided type error.
         * Declared as a settable field rather than a constructor parameter so the many {@code MatchContext(...)}
         * call sites need no changes; it is propagated only along the copy methods and the protocol
         * sub-context, which are the parts of the match that form the user-facing failure narrative.
         */
        @Nullable DiagnosticsCollector diagnostics = null;

        /**
         * The element a collected breakdown is reported on, used to resolve type and class names to their
         * declarations so the tooltip can show clickable links. Set together with {@link #diagnostics} by
         * {@link #explainMismatch} and propagated, like it, along the copy methods and the protocol sub-context.
         */
        @Nullable PsiElement anchor = null;

        MatchContext(TypeEvalContext context, GenericSubstitutions substitutions, boolean reversedSubstitutions) {
            this.context = context;
            this.mySubstitutions = substitutions;
            this.reversedSubstitutions = reversedSubstitutions;
        }

        MatchContext reverseSubstitutions() {
            MatchContext result = new MatchContext(context, mySubstitutions, !reversedSubstitutions);
            result.diagnostics = diagnostics;
            result.anchor = anchor;
            return result;
        }

        MatchContext resetSubstitutions() {
            MatchContext result = new MatchContext(context, mySubstitutions, false);
            result.diagnostics = diagnostics;
            result.anchor = anchor;
            return result;
        }
    }

    /**
     * Accumulates the {@link PyTypeMismatchExplanation} tree as {@link #recordFrame}/{@link #recordLeaf} descend the failing path.
     */
    private static final class DiagnosticsCollector {
        List<PyTypeMismatchExplanation> current = new ArrayList<>();
    }
}
