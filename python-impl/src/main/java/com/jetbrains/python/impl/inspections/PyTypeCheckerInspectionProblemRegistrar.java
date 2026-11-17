// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.google.common.collect.Sets;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.inspections.PyTypeCheckerInspection.AnalyzeArgumentResult;
import com.jetbrains.python.impl.inspections.PyTypeCheckerInspection.AnalyzeCalleeResults;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeMismatchExplanation;
import com.jetbrains.python.impl.psi.types.PyTypeUtilKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.impl.psi.types.PyLiteralStringType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.HtmlBuilder;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class PyTypeCheckerInspectionProblemRegistrar {
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private PyTypeCheckerInspectionProblemRegistrar() {
    }

    @RequiredReadAction
    static void registerProblem(ProblemsHolder holder,
                                PyElement callSite,
                                List<AnalyzeCalleeResults> calleesResults,
                                TypeEvalContext context,
                                @Nullable ProblemHighlightType highlightOverride) {
        PyTypeCheckerSuppressionCode code = suppressionCodeFor(callSite);
        if (calleesResults.size() == 1) {
            registerSingleCalleeProblem(
                holder,
                code,
                callSite,
                calleesResults.get(0),
                context,
                highlightOverride
            );
        }
        else if (!calleesResults.isEmpty()) {
            registerMultiCalleeProblem(holder, code, callSite, calleesResults, context, highlightOverride);
        }
    }

    /**
     * Argument mismatches map to {@link PyTypeCheckerSuppressionCode#BAD_ARGUMENT_TYPE}, except when the call site is
     * an operator (binary / augmented assignment) or a subscription, which get their own dedicated codes.
     */
    private static PyTypeCheckerSuppressionCode suppressionCodeFor(PyElement callSite) {
        if (callSite instanceof PyBinaryExpression || callSite instanceof PyAugAssignmentStatement) {
            return PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR;
        }
        if (callSite instanceof PySubscriptionExpression) {
            return PyTypeCheckerSuppressionCode.BAD_INDEX;
        }
        return PyTypeCheckerSuppressionCode.BAD_ARGUMENT_TYPE;
    }

    @RequiredReadAction
    private static void registerSingleCalleeProblem(ProblemsHolder holder,
                                                    PyTypeCheckerSuppressionCode code,
                                                    PyElement callSite,
                                                    AnalyzeCalleeResults calleeResults,
                                                    TypeEvalContext context,
                                                    @Nullable ProblemHighlightType highlightOverride) {
        for (AnalyzeArgumentResult argumentResult : calleeResults.getResults()) {
            if (argumentResult.isMatched()) {
                continue;
            }

            PyExpression argument = argumentResult.getArgument();
            PyInspectionMessages.ProblemMessage message = getSingleCalleeProblemMessage(argumentResult, context);
            ProblemHighlightType type = highlightOverride != null ? highlightOverride : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
            // The breakdown tooltip re-runs the match, so reportWithTooltip invokes the supplier only on-the-fly.
            PyType expectedTypeAfterSubstitution = PyTypeUtilKt.isUnknown(argumentResult.getExpectedTypeAfterSubstitution())
                ? null
                : argumentResult.getExpectedTypeAfterSubstitution();
            PyType expected = expectedTypeAfterSubstitution != null ? expectedTypeAfterSubstitution : argumentResult.getExpectedType();
            PyTypeCheckerProblemReporter.reportWithTooltip(holder, code, argument, message, type, () -> {
                // The call site lets the breakdown read as "f() needs parameter 'a' … / 'x' is …" instead of a bare tree.
                PyType actualType = argumentResult.getActualType();
                PyCallableParameter parameter = argumentResult.getParameter();
                PyTypeMismatchProse.CallSite proseCallSite = new PyTypeMismatchProse.CallSite(
                    calleeParam(calleeResults.getCallable()),
                    shortenExpression(argument),
                    parameter != null ? parameter.getName() : null,
                    PyInspectionMessages.CodifiedParam.ofType(expected, argument, context),
                    PyInspectionMessages.CodifiedParam.ofType(actualType, argument, context),
                    // A literal actual (e.g. `"a"`, `42`) restates the argument expression, so its type clause is dropped.
                    actualType instanceof PyLiteralType || actualType instanceof PyLiteralStringType
                );
                return breakdownTooltip(message,
                                        expected,
                                        argumentResult.getActualType(),
                                        context,
                                        argument,
                                        proseCallSite);
            });
        }

        for (PyTypeCheckerInspection.UnexpectedArgumentForParamSpec unexpectedArgumentForParamSpec : calleeResults.getUnmatchedArguments()) {
            PyExpression argument = unexpectedArgumentForParamSpec.getArgument();
            String paramSpecTypeName = unexpectedArgumentForParamSpec.getParamSpecType().getVariableName();
            registerWithOverride(
                holder, code, argument,
                PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerUnexpectedArgumentFromParamspec(p[0]), paramSpecTypeName),
                highlightOverride
            );
        }

        if (callSite instanceof PyCallExpression callExpression) {
            PyArgumentList argumentList = callExpression.getArgumentList();
            if (argumentList != null) {
                PsiElement rpar = PyPsiUtils.getFirstChildOfType(argumentList, PyTokenTypes.RPAR);
                if (rpar != null) {
                    for (PyTypeCheckerInspection.UnfilledParameterFromParamSpec unfilledParameterFromParamSpec : calleeResults.getUnmatchedParameters()) {
                        String parameterName = unfilledParameterFromParamSpec.getParameter().getName();
                        String paramSpecTypeName = unfilledParameterFromParamSpec.getParamSpecType().getVariableName();
                        if (parameterName != null) {
                            registerWithOverride(
                                holder, code, rpar, PyInspectionMessages.problemMessage(
                                    p -> PyLocalize.inspTypeCheckerUnfilledParameterForParamspec(p[0], p[1]), parameterName,
                                    paramSpecTypeName
                                ), highlightOverride
                            );
                        }
                    }

                    for (PyTypeCheckerInspection.UnfilledPositionalVararg unfilledParameterFromParamSpec : calleeResults.getUnfilledPositionalVarargs()) {
                        String varargName = unfilledParameterFromParamSpec.varargName();
                        PyInspectionMessages.CodifiedParam expectedType =
                            PyInspectionMessages.CodifiedParam.ofType(unfilledParameterFromParamSpec.expectedType(), rpar, context);
                        registerWithOverride(
                            holder, code, rpar,
                            PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerUnfilledVararg(p[0], p[1]), varargName, expectedType),
                            highlightOverride
                        );
                    }
                }
            }
        }
    }

    @RequiredReadAction
    private static void registerWithOverride(ProblemsHolder holder,
                                             PyTypeCheckerSuppressionCode code,
                                             PsiElement element,
                                             PyInspectionMessages.ProblemMessage message,
                                             @Nullable ProblemHighlightType highlightOverride) {
        ProblemHighlightType type = highlightOverride != null ? highlightOverride : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        PyTypeCheckerProblemReporter.report(holder, code, element, message, type);
    }

    @RequiredReadAction
    private static void registerMultiCalleeProblem(ProblemsHolder holder,
                                                   PyTypeCheckerSuppressionCode code,
                                                   PyElement callSite,
                                                   List<AnalyzeCalleeResults> calleesResults,
                                                   TypeEvalContext context,
                                                   @Nullable ProblemHighlightType highlightOverride) {
        if (callSite instanceof PyBinaryExpression binaryExpression) {
            registerMultiCalleeProblemForBinaryExpression(holder, code, binaryExpression, calleesResults, context, highlightOverride);
        }
        else {
            registerMultiCalleeProblem(holder, code, getMultiCalleeElementToHighlight(callSite), calleesResults, context, highlightOverride);
        }
    }

    @RequiredReadAction
    private static void registerMultiCalleeProblem(ProblemsHolder holder,
                                                   PyTypeCheckerSuppressionCode code,
                                                   @Nullable PsiElement element,
                                                   List<AnalyzeCalleeResults> calleesResults,
                                                   TypeEvalContext context,
                                                   @Nullable ProblemHighlightType highlightOverride) {
        PyInspectionMessages.ProblemMessage header = PyMismatchTooltips.header(calleesResults.stream().map(AnalyzeCalleeResults::getCallable).toList());
        List<PyMismatchTooltips.Slot> argumentSlots = new ArrayList<>();
        for (AnalyzeArgumentResult argumentResult : getReferenceResults(calleesResults)) {
            argumentSlots.add(PyMismatchTooltips.Slot.argument(argumentResult.getArgument(), argumentResult.getActualType(), context,
                                                               !argumentMatchesNoCallee(argumentResult.getArgument(), calleesResults)));
        }
        List<List<PyMismatchTooltips.Slot>> expectedRows = new ArrayList<>();
        for (AnalyzeCalleeResults calleeResults : calleesResults) {
            expectedRows.add(expectedParameterRow(calleeResults, context));
        }
        // Each candidate's source type-parameter list (`[T: int]`, empty when it declares none), shown before its params.
        List<String> expectedTypeParameters = new ArrayList<>();
        for (AnalyzeCalleeResults calleeResults : calleesResults) {
            expectedTypeParameters.add(typeParameterPrefix(calleeResults.getCallable()));
        }

        String description = PyMismatchTooltips.description(header, argumentSlots, expectedRows, expectedTypeParameters);
        ProblemHighlightType highlightType = highlightOverride != null ? highlightOverride : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        // The aligned-table tooltip is only worth building on-the-fly; reportWithTooltip invokes the supplier then.
        PyTypeCheckerProblemReporter.reportWithTooltip(holder, code, element, description, highlightType,
                                                       () -> PyMismatchTooltips.tooltip(header, argumentSlots, expectedRows, expectedTypeParameters));
    }

    /**
     * A callable's type-parameter list in source form ({@code [T: int]}), or empty when it declares none (or is not a
     * {@code def}/{@code async def} — a lambda or synthesized callable can't be generic).
     */
    @RequiredReadAction
    private static String typeParameterPrefix(@Nullable PyCallable callable) {
        PyTypeParameterList typeParameterList = callable instanceof PyTypeParameterListOwner owner ? owner.getTypeParameterList() : null;
        if (typeParameterList == null) {
            return "";
        }
        // The grid shows each candidate on one line, so a type-parameter list that spans several lines is collapsed.
        return typeParameterList.getTypeParameters().isEmpty() ? "" : WHITESPACE_RUN.matcher(typeParameterList.getText()).replaceAll(" ");
    }

    private static PyInspectionMessages.ProblemMessage getSingleCalleeProblemMessage(AnalyzeArgumentResult argumentResult,
                                                                                     TypeEvalContext context) {
        PyType actualType = argumentResult.getActualType();
        PyType expectedType = argumentResult.getExpectedType();

        Objects.requireNonNull(actualType); // see PyTypeCheckerInspection.Visitor.analyzeArgument()
        Objects.requireNonNull(expectedType); // see PyTypeCheckerInspection.Visitor.analyzeArgument()

        PyExpression anchor = argumentResult.getArgument();
        PyInspectionMessages.CodifiedParam actualTypeParam = PyInspectionMessages.CodifiedParam.ofType(actualType, anchor, context);

        if (expectedType instanceof PyStructuralType structuralType) {
            Set<String> expectedAttributes = structuralType.getAttributeNames();
            Set<String> actualAttributes = getAttributes(actualType, context);

            if (actualAttributes != null) {
                Set<String> missingAttributes = Sets.difference(expectedAttributes, actualAttributes);
                return PyInspectionMessages.problemMessage(
                    p -> PyLocalize.inspTypeCheckerTypeDoesNotHaveExpectedAttribute(p[0], p[1], p[2]),
                    actualTypeParam, missingAttributes.size(),
                    PyInspectionMessages.CodifiedParam.joinNames(missingAttributes.stream().filter(Objects::nonNull).toList())
                );
            }
        }

        PyType expectedTypeAfterSubstitution = argumentResult.getExpectedTypeAfterSubstitution();
        PyInspectionMessages.CodifiedParam expectedTypeParam = PyInspectionMessages.CodifiedParam.ofType(expectedType, anchor, context, true);
        PyInspectionMessages.CodifiedParam expectedSubstitutedParam =
            !PyTypeUtilKt.isUnknown(expectedTypeAfterSubstitution) && !Objects.equals(expectedTypeAfterSubstitution, expectedType)
                ? PyInspectionMessages.CodifiedParam.ofType(expectedTypeAfterSubstitution, anchor, context)
                : null;

        if (PyProtocolsKt.matchingProtocolDefinitions(expectedType, actualType, context)) {
            if (expectedSubstitutedParam != null) {
                return PyInspectionMessages.problemMessage(
                    p -> PyLocalize.inspTypeCheckerOnlyConcreteClassCanBeUsedWhereMatchedProtocolExpected(p[0], p[1]),
                    expectedSubstitutedParam, expectedTypeParam
                );
            }
            else {
                return PyInspectionMessages.problemMessage(
                    p -> PyLocalize.inspTypeCheckerOnlyConcreteClassCanBeUsedWhereProtocolExpected(p[0]), expectedTypeParam);
            }
        }

        if (expectedSubstitutedParam != null) {
            return enrichWithCallableDiff(
                PyInspectionMessages.problemMessage(
                    p -> PyLocalize.inspTypeCheckerExpectedMatchedTypeGotTypeInstead(p[0], p[1], p[2]), expectedSubstitutedParam, expectedTypeParam,
                    actualTypeParam
                ),
                expectedTypeAfterSubstitution, actualType, context
            );
        }
        else {
            return enrichWithCallableDiff(
                PyInspectionMessages.problemMessage(
                    p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]), expectedTypeParam, actualTypeParam),
                expectedType, actualType, context
            );
        }
    }

    /**
     * Replaces the tooltip of {@code base} with an aligned callable type diff (see {@link PyTypeDiff}) when both
     * {@code expected} and {@code actual} are callables; otherwise returns {@code base} unchanged. The plain-text description is
     * always preserved for the Problems view.
     */
    private static PyInspectionMessages.ProblemMessage enrichWithCallableDiff(PyInspectionMessages.ProblemMessage base,
                                                                              @Nullable PyType expected,
                                                                              @Nullable PyType actual,
                                                                              TypeEvalContext context) {
        String diff = PyTypeDiff.diffTooltip(expected, actual, context);
        return diff != null ? new PyInspectionMessages.ProblemMessage(base.description(), diff) : base;
    }

    @RequiredReadAction
    private static void registerMultiCalleeProblemForBinaryExpression(ProblemsHolder holder,
                                                                      PyTypeCheckerSuppressionCode code,
                                                                      PyBinaryExpression binaryExpression,
                                                                      List<AnalyzeCalleeResults> calleesResults,
                                                                      TypeEvalContext context,
                                                                      @Nullable ProblemHighlightType highlightOverride) {
        java.util.function.Predicate<AnalyzeCalleeResults> isRightOperatorResults =
            calleeResults -> binaryExpression.isRightOperator(calleeResults.getCallable());

        boolean allCalleesAreRightOperators = calleesResults.stream().allMatch(isRightOperatorResults);

        List<AnalyzeCalleeResults> preferredOperatorsResults =
            allCalleesAreRightOperators
                ? calleesResults
                : calleesResults.stream().filter(it -> !isRightOperatorResults.test(it)).toList();

        if (preferredOperatorsResults.size() == 1) {
            registerSingleCalleeProblem(
                holder,
                code,
                binaryExpression,
                preferredOperatorsResults.get(0),
                context,
                highlightOverride
            );
        }
        else {
            registerMultiCalleeProblem(
                holder,
                code,
                allCalleesAreRightOperators ? binaryExpression.getLeftExpression() : binaryExpression.getRightExpression(),
                preferredOperatorsResults, context, highlightOverride
            );
        }
    }

    private static PsiElement getMultiCalleeElementToHighlight(PyElement callSite) {
        if (callSite instanceof PyCallExpression callExpression) {
            PyArgumentList argumentList = callExpression.getArgumentList();

            PsiElement result = Optional
                .ofNullable(argumentList)
                .map(PyArgumentList::getArguments)
                .filter(it -> it.length == 1)
                .<PsiElement>map(it -> it[0])
                .orElse(argumentList);

            return result != null ? result : callSite;
        }
        else if (callSite instanceof PySubscriptionExpression subscriptionExpression) {
            PyExpression indexExpression = subscriptionExpression.getIndexExpression();
            return indexExpression != null ? indexExpression : callSite;
        }
        return callSite;
    }

    /**
     * Results of the callee that maps the most arguments; used as the source of actual argument types,
     * so that their order is consistent with the per-callee expected parameter rows.
     */
    private static List<AnalyzeArgumentResult> getReferenceResults(List<AnalyzeCalleeResults> calleesResults) {
        List<AnalyzeArgumentResult> best = null;
        for (AnalyzeCalleeResults calleeResults : calleesResults) {
            List<AnalyzeArgumentResult> results = calleeResults.getResults();
            if (best == null || results.size() > best.size()) {
                best = results;
            }
        }
        return best != null ? best : List.of();
    }

    private static boolean argumentMatchesNoCallee(PyExpression argument, List<AnalyzeCalleeResults> calleesResults) {
        return calleesResults.stream().noneMatch(
            calleeResults -> calleeResults.getResults().stream().anyMatch(it -> it.getArgument() == argument && it.isMatched())
        );
    }

    private static @Nullable Set<String> getAttributes(PyType type, TypeEvalContext context) {
        if (type instanceof PyStructuralType structuralType) {
            return structuralType.getAttributeNames();
        }
        else if (type instanceof PyClassLikeType classLikeType) {
            return classLikeType.getMemberNames(true, context);
        }
        return null;
    }

    /**
     * One candidate signature's "Expected one of" row, built from its FULL parameter list — not only the parameters
     * an argument mapped to — so an optional parameter the call omits is still shown, and a required parameter it
     * leaves unfilled is shown as wholly missing (name and type highlighted). A parameter an argument mapped to keeps
     * its per-argument result (its type highlighted only on a real type mismatch).
     */
    private static List<PyMismatchTooltips.Slot> expectedParameterRow(AnalyzeCalleeResults calleeResults, TypeEvalContext context) {
        List<PyCallableParameter> parameters = calleeResults.getCallableType().getParameters(context);
        if (parameters == null) {
            return calleeResults.getResults().stream().map(it -> getExpectedParameterSlot(it, context, it.isMatched())).toList();
        }
        List<PyMismatchTooltips.Slot> slots = new ArrayList<>();
        for (PyCallableParameter parameter : parameters) {
            if (parameter.isPositionOnlySeparator() || parameter.isKeywordOnlySeparator()) {
                continue;
            }
            AnalyzeArgumentResult argumentResult = calleeResults.getResults().stream()
                .filter(it -> it.getParameter() == parameter)
                .findFirst()
                .orElse(null);
            if (argumentResult != null) {
                slots.add(getExpectedParameterSlot(argumentResult, context, argumentResult.isMatched()));
            }
            // A required parameter the call can't fill: the whole parameter is the incompatibility (name + type).
            else if (calleeResults.getUnfilledRegularParameters().stream().anyMatch(it -> it == parameter)) {
                slots.add(PyMismatchTooltips.Slot.parameter(parameter, context, false, true));
            }
            // An optional (or otherwise unmapped) parameter: shown for context, not flagged.
            else {
                slots.add(PyMismatchTooltips.Slot.parameter(parameter, context, true));
            }
        }
        return slots;
    }

    private static PyMismatchTooltips.Slot getExpectedParameterSlot(AnalyzeArgumentResult argumentResult,
                                                                    TypeEvalContext context,
                                                                    boolean matched) {
        // Show the parameter's DECLARED type (e.g. the type variable `T`), not the type solved for it at this call site
        // (`int`) — the signature is written in terms of its type parameters, which are shown as a `[T: bound]` prefix.
        PyCallableParameter parameter = argumentResult.getParameter();
        if (parameter == null) {
            return PyMismatchTooltips.Slot.ofType(argumentResult.getExpectedType(), context, matched);
        }
        return PyMismatchTooltips.Slot.parameter(parameter, context, matched);
    }

    /**
     * The argument expression as a single-line, length-capped label for the call-site prose (e.g. {@code to_app_page()}).
     */
    @RequiredReadAction
    private static String shortenExpression(PyExpression expression) {
        return StringUtil.shortenTextWithEllipsis(WHITESPACE_RUN.matcher(expression.getText()).replaceAll(" ").trim(), 40, 0);
    }

    /**
     * The callee rendered as a clickable {@code name()} span (a navigable {@code #element/} link to the function/method) for
     * the call-site prose; null for an anonymous callee, which disables the framing.
     */
    private static PyInspectionMessages.@Nullable CodifiedParam calleeParam(@Nullable PyCallable callable) {
        String name = callable != null ? callable.getName() : null;
        if (name == null) {
            return null;
        }
        return PyInspectionMessages.CodifiedParam.ofReference(callable, name + "()");
    }

    /**
     * Renders {@code headlineFragment} (already an HTML fragment, with any {@code <code>} spans) followed by the {@code explanations}
     * trees, folded into prose by {@link PyTypeMismatchProse}, as an HTML tooltip (on-the-fly only). The result is used as
     * the problem's tooltip, not its description, so batch results stay one line.
     * <p>
     * When the mismatch is a single call-argument failure ({@code callSite} supplied), the two-line requirement/actual
     * framing replaces the headline instead; several independent reasons always keep the headline above them.
     */
    private static String breakdownTooltipFromFragment(String headlineFragment,
                                                       List<PyTypeMismatchExplanation> explanations,
                                                       PyTypeMismatchProse.@Nullable CallSite callSite) {
        HtmlBuilder builder = new HtmlBuilder();
        // When the mismatch is a call argument, prefer the two-line requirement/actual framing, which names the
        // callee and argument and replaces the "Expected type …, got …" headline. It returns null for shapes it
        // doesn't reframe, in which case we keep the headline followed by the plain folded breakdown.
        List<PyTypeMismatchProse.ProseLine> framed =
            explanations.size() == 1 && callSite != null ? PyTypeMismatchProse.argumentProseLines(callSite, explanations.get(0)) : null;
        if (framed != null) {
            PyTypeMismatchProse.appendLines(builder, framed, false);
        }
        else {
            builder.appendRaw(headlineFragment);
            PyTypeMismatchProse.appendLines(builder, PyTypeMismatchProse.proseLines(explanations), true);
        }
        return builder.wrapWith("html").toString();
    }

    /**
     * {@link #breakdownTooltipFromFragment} with an enriched headline; its {@code <code>} spans (and any links) are kept.
     */
    public static String breakdownTooltip(PyInspectionMessages.ProblemMessage headline,
                                          PyTypeMismatchExplanation explanation) {
        return breakdownTooltip(headline, explanation, (PyTypeMismatchProse.CallSite) null);
    }

    /**
     * {@link #breakdownTooltipFromFragment} with an enriched headline; its {@code <code>} spans (and any links) are kept.
     */
    public static String breakdownTooltip(PyInspectionMessages.ProblemMessage headline,
                                          PyTypeMismatchExplanation explanation,
                                          PyTypeMismatchProse.@Nullable CallSite callSite) {
        return breakdownTooltipFromFragment(PyInspectionMessages.tooltipFragment(headline), List.of(explanation), callSite);
    }

    /**
     * {@link #breakdownTooltip} for a headline explained by several independent reasons, each folded into its own prose line.
     */
    public static @Nullable String breakdownTooltip(PyInspectionMessages.ProblemMessage headline,
                                                    List<PyTypeMismatchExplanation> explanations) {
        return explanations.isEmpty()
            ? null
            : breakdownTooltipFromFragment(PyInspectionMessages.tooltipFragment(headline), explanations, null);
    }

    /**
     * The breakdown tooltip explaining why {@code actual} doesn't match {@code expected}, or null when the failure category
     * isn't instrumented (no {@link PyTypeChecker#explainMismatch} result). Pass as the on-the-fly tooltip supplier to
     * {@link PyInspectionVisitor#registerProblemWithTooltip}; it re-runs the match, so it must be invoked only on-the-fly.
     * <p>
     * {@code anchor} is the element the problem is reported on; it is used to resolve type and class names in the
     * breakdown to their declarations so they render as clickable links (as in the enriched headline).
     */
    public static @Nullable String breakdownTooltip(PyInspectionMessages.ProblemMessage headline,
                                                    @Nullable PyType expected,
                                                    @Nullable PyType actual,
                                                    TypeEvalContext context,
                                                    @Nullable PsiElement anchor) {
        return breakdownTooltip(headline, expected, actual, context, anchor, null);
    }

    /**
     * The breakdown tooltip explaining why {@code actual} doesn't match {@code expected}, or null when the failure category
     * isn't instrumented (no {@link PyTypeChecker#explainMismatch} result).
     * <p>
     * {@code callSite}, when the mismatch is a call argument, reframes the breakdown as a two-line requirement/actual
     * sentence naming the callee and argument (see {@link PyTypeMismatchProse#argumentProseLines}).
     */
    public static @Nullable String breakdownTooltip(PyInspectionMessages.ProblemMessage headline,
                                                    @Nullable PyType expected,
                                                    @Nullable PyType actual,
                                                    TypeEvalContext context,
                                                    @Nullable PsiElement anchor,
                                                    PyTypeMismatchProse.@Nullable CallSite callSite) {
        PyTypeMismatchExplanation explanation = PyTypeChecker.explainMismatch(expected, actual, context, anchor);
        return explanation != null ? breakdownTooltip(headline, explanation, callSite) : null;
    }
}
