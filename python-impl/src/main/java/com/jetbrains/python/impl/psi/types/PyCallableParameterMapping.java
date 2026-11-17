// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Matches signatures of callables according to the Callable compatibility rules
 *
 * @see <a href="https://typing.python.org/en/latest/spec/callables.html#assignability-rules-for-callables">Specification</a>
 */
public final class PyCallableParameterMapping {
    private PyCallableParameterMapping() {
    }

    private enum ParameterKind {
        POSITIONAL_ONLY,
        POSITIONAL_OR_KEYWORD,
        KEYWORD_ONLY,
        POSITIONAL_CONTAINER,
        KEYWORD_CONTAINER,
        TYPE_VAR_TUPLE;
    }

    private record Parameter(PyCallableParameter parameter, ParameterKind kind) {
        @Nullable String getName() {
            return parameter.getName();
        }

        boolean getHasDefault() {
            return parameter.hasDefaultValue();
        }

        @Nullable PyType getArgumentType(TypeEvalContext context) {
            return parameter.getArgumentType(context);
        }

        boolean getAcceptsPositionalArgument() {
            return kind == ParameterKind.POSITIONAL_ONLY || kind == ParameterKind.POSITIONAL_OR_KEYWORD || kind == ParameterKind.POSITIONAL_CONTAINER;
        }

        Parameter copy(ParameterKind kind) {
            return new Parameter(parameter, kind);
        }
    }

    public static @Nullable PyTypeParameterMapping mapCallableParameters(List<PyCallableParameter> expectedCallableParameters,
                                                                         List<PyCallableParameter> actualCallableParameters,
                                                                         TypeEvalContext context) {
        return mapCallableParameters(expectedCallableParameters, actualCallableParameters, context, null);
    }

    public static @Nullable PyTypeParameterMapping mapCallableParameters(List<PyCallableParameter> expectedCallableParameters,
                                                                         List<PyCallableParameter> actualCallableParameters,
                                                                         TypeEvalContext context,
                                                                         @Nullable Consumer<ProblemMessage> mismatch) {
        return mapCallableParameters(expectedCallableParameters, actualCallableParameters, context, mismatch, null);
    }

    /**
     * Maps a list of expected parameters to a list of actual parameters for a Callable
     * by analyzing their signatures and attempting to align them.
     *
     * @param expectedCallableParameters a list of callable parameters from the expected Callable.
     * @param actualCallableParameters   a list of callable parameters from the actual Callable.
     * @param context                    TypeEvalContext.
     * @param mismatch                   optional sink, invoked at most once with a localized reason when the signatures do not match
     *                                   by structure. Pass {@code null} (the default) during normal matching for zero overhead; supply a collector
     *                                   only when building a {@link PyTypeChecker#explainMismatch} breakdown.
     * @param parameterNames             optional out-list collecting the name of the parameter behind each mapped type pair, in the
     *                                   same order they are added to the type lists. Populated only for plain positional/keyword parameters; when a
     *                                   container ({@code *args}/{@code **kwargs}) or variadic is involved the list is left shorter than the mapping so callers
     *                                   can detect the misalignment by size and fall back to a generic, position-less message. Pass {@code null} (the
     *                                   default) during normal matching for zero overhead.
     * @return a {@code PyCallableParameterMapping} object representing the result of the parameter mapping,
     * or {@code null} if one of the signatures is specified incorrectly, or they do not match by structure
     */
    public static @Nullable PyTypeParameterMapping mapCallableParameters(List<PyCallableParameter> expectedCallableParameters,
                                                                         List<PyCallableParameter> actualCallableParameters,
                                                                         TypeEvalContext context,
                                                                         @Nullable Consumer<ProblemMessage> mismatch,
                                                                         @Nullable List<@Nullable String> parameterNames) {
        if (!handleUnwrappedTypedDict(expectedCallableParameters, actualCallableParameters, context)) {
            return null;
        }

        List<Parameter> expectedCategorizedParameters = categorizeParameters(expectedCallableParameters, context);
        if (expectedCategorizedParameters == null) {
            return null;
        }
        List<Parameter> actualCategorizedParameters = categorizeParameters(actualCallableParameters, context);
        if (actualCategorizedParameters == null) {
            return null;
        }

        // Special handling for wildcard signatures (*args, **kwargs)
        // Wildcard signatures match with any other signature
        if (ParamHelper.isWildcardSignature(expectedCallableParameters, context)) {
            return PyTypeParameterMapping.mapByShape(Collections.emptyList(), Collections.emptyList());
        }

        ArrayDeque<Parameter> expectedParameters = new ArrayDeque<>(expectedCategorizedParameters);
        ArrayDeque<Parameter> actualParameters = new ArrayDeque<>(actualCategorizedParameters);

        List<@Nullable PyType> expectedTypes = new ArrayList<>();
        List<@Nullable PyType> actualTypes = new ArrayList<>();

        Parameter actualKeywordContainer = actualParameters.stream().filter(it -> it.kind() == ParameterKind.KEYWORD_CONTAINER).findFirst().orElse(null);

        boolean actualHasTypeVarTuple = actualParameters.stream().anyMatch(it -> it.kind() == ParameterKind.TYPE_VAR_TUPLE);

        Map<String, Parameter> actualKeywordOrPositional = new LinkedHashMap<>();
        for (Parameter it : actualParameters) {
            if ((it.kind() == ParameterKind.POSITIONAL_OR_KEYWORD ||
                it.kind() == ParameterKind.KEYWORD_ONLY) && it.getName() != null) {
                actualKeywordOrPositional.put(it.getName(), it);
            }
        }

        /*
         * if Callable contains TypeVarTuple, say, Callable[[int, str, *Ts, bool, int], None]
         * we need to calculate how many *positional-only* parameters
         * (positional including positional container in case of real signature)
         * are expected from the right side of the TypeVarTuple.
         */
        long actualPositionalOrContainerCount = actualParameters.stream().filter(Parameter::getAcceptsPositionalArgument).count();
        long expectedPositionalOrContainerCount = expectedParameters.stream().filter(Parameter::getAcceptsPositionalArgument).count();

        long paramsTypeVarTupleShouldAccept =
            actualPositionalOrContainerCount - expectedPositionalOrContainerCount + (actualHasTypeVarTuple ? 1 : 0);

        while (!expectedParameters.isEmpty() && !actualParameters.isEmpty()) {
            Parameter expectedParameter = expectedParameters.peek();
            Parameter actualParameter = actualParameters.peek();

            switch (expectedParameter.kind()) {
                // Positional-only can match with positional or positional-or-keyword parameters
                case POSITIONAL_ONLY -> {
                    if (actualParameter.parameter().isPositionalContainer()) {
                        expectedTypes.add(expectedParameter.getArgumentType(context));
                        actualTypes.add(actualParameter.getArgumentType(context));
                        expectedParameters.pop();
                    }
                    else if (actualParameter.getAcceptsPositionalArgument()) {
                        if (expectedParameter.getHasDefault() && !actualParameter.getHasDefault()) {
                            if (mismatch != null) {
                                mismatch.accept(defaultRequired(expectedParameter.getName()));
                            }
                            return null;
                        }
                        expectedTypes.add(expectedParameter.getArgumentType(context));
                        expectedParameters.pop();
                        actualTypes.add(actualParameter.getArgumentType(context));
                        actualParameters.pop();
                        if (parameterNames != null) {
                            parameterNames.add(actualParameter.getName() != null ? actualParameter.getName() : expectedParameter.getName());
                        }
                    }
                    else {
                        if (mismatch != null) {
                            mismatch.accept(incompatibleKind(expectedParameter.getName()));
                        }
                        return null;
                    }
                }
                // Must match by name
                case POSITIONAL_OR_KEYWORD -> {
                    if (actualParameter.kind() == ParameterKind.POSITIONAL_OR_KEYWORD) {
                        if (!java.util.Objects.equals(expectedParameter.getName(), actualParameter.getName())) {
                            if (mismatch != null) {
                                mismatch.accept(nameMismatch(expectedParameter.getName(), actualParameter.getName()));
                            }
                            return null;
                        }
                        else if (expectedParameter.getHasDefault() && !actualParameter.getHasDefault()) {
                            if (mismatch != null) {
                                mismatch.accept(defaultRequired(expectedParameter.getName()));
                            }
                            return null;
                        }
                        expectedTypes.add(expectedParameter.getArgumentType(context));
                        expectedParameters.pop();
                        actualTypes.add(actualParameter.getArgumentType(context));
                        actualParameters.pop();
                        if (parameterNames != null) {
                            parameterNames.add(actualParameter.getName() != null ? actualParameter.getName() : expectedParameter.getName());
                        }
                    }
                    else if (actualParameter.kind() == ParameterKind.POSITIONAL_CONTAINER && actualKeywordContainer != null) {
                        expectedTypes.add(expectedParameter.getArgumentType(context));
                        actualTypes.add(actualParameter.getArgumentType(context));
                        expectedParameters.pop();
                    }
                    else {
                        if (mismatch != null) {
                            mismatch.accept(incompatibleKind(expectedParameter.getName()));
                        }
                        return null;
                    }
                }
                // Keyword-only parameter must match by name from the set of keyword-only or
                // positional-or-keyword parameters from the actual signature
                case KEYWORD_ONLY -> {
                    Parameter actualKwOnlyOrPositionalParam = actualKeywordOrPositional.remove(expectedParameter.getName());
                    if (actualKwOnlyOrPositionalParam != null) {
                        if (expectedParameter.getHasDefault() && !actualKwOnlyOrPositionalParam.getHasDefault()) {
                            if (mismatch != null) {
                                mismatch.accept(defaultRequired(expectedParameter.getName()));
                            }
                            return null;
                        }
                        expectedTypes.add(expectedParameter.getArgumentType(context));
                        expectedParameters.pop();
                        actualTypes.add(actualKwOnlyOrPositionalParam.getArgumentType(context));
                        if (!actualParameters.remove(actualKwOnlyOrPositionalParam)) {
                            throw new IllegalArgumentException("Failed requirement.");
                        }
                        if (parameterNames != null) {
                            parameterNames.add(actualKwOnlyOrPositionalParam.getName() != null
                                                   ? actualKwOnlyOrPositionalParam.getName()
                                                   : expectedParameter.getName());
                        }
                    }
                    else if (actualKeywordContainer != null) {
                        expectedTypes.add(expectedParameter.getArgumentType(context));
                        actualTypes.add(actualKeywordContainer.getArgumentType(context));
                        // All keyword parameters and the corresponding container are consumed, so we can pop the container
                        expectedParameters.pop();
                    }
                    else {
                        if (mismatch != null) {
                            mismatch.accept(missingParameter(expectedParameter.getName()));
                        }
                        return null;
                    }
                }
                // *args can consume multiple positional parameters
                case POSITIONAL_CONTAINER -> {
                    PyType argsType = expectedParameter.getArgumentType(context);
                    // Consume all remaining parameters until we meet the corresponding *args container (error otherwise)
                    Parameter actualPositional = actualParameters.pop();
                    while (!actualParameters.isEmpty() && !actualPositional.parameter().isPositionalContainer()) {
                        // Parameter can't be omitted, or there can't possibly be *args in the actual signature after it
                        if (!actualPositional.getHasDefault() || !actualPositional.getAcceptsPositionalArgument()) {
                            return null;
                        }
                        expectedTypes.add(argsType);
                        actualTypes.add(actualPositional.getArgumentType(context));
                        actualPositional = actualParameters.pop();
                    }
                    // we need to match containers themselves as well (e.g. *args: T <- *args: T1)
                    if (actualPositional.parameter().isPositionalContainer()) {
                        // *args: T is not propagated to *tuple[T, ...] here to avoid conflicts with mapping of subsequent types
                        expectedTypes.add(argsType);
                        actualTypes.add(actualPositional.getArgumentType(context));
                        // All positional parameters and the corresponding container are consumed, so we can pop the container
                        expectedParameters.pop();
                    }
                    else {
                        // Positional container must be present in the actual signature
                        return null;
                    }
                }
                // **kwargs can consume multiple keyword parameters
                case KEYWORD_CONTAINER -> {
                    PyType kwargsType = expectedParameter.getArgumentType(context);
                    // Consume all remaining parameters until we meet the corresponding **kwargs container (error otherwise)
                    Parameter actualKeywordParam = actualParameters.pop();
                    while (!actualParameters.isEmpty() && !actualKeywordParam.parameter().isKeywordContainer()) {
                        // Parameter can't be omitted
                        if (!actualKeywordParam.getHasDefault() && actualKeywordParam.kind() != ParameterKind.POSITIONAL_CONTAINER) {
                            return null;
                        }
                        // skip the types of positional-only parameters with defaults and positional container
                        if (actualKeywordParam.kind() == ParameterKind.KEYWORD_ONLY ||
                            actualKeywordParam.kind() == ParameterKind.POSITIONAL_OR_KEYWORD) {
                            expectedTypes.add(kwargsType);
                            actualTypes.add(actualKeywordParam.getArgumentType(context));
                        }
                        actualKeywordParam = actualParameters.pop();
                    }
                    // match keyword containers themselves
                    if (actualKeywordParam.parameter().isKeywordContainer()) {
                        expectedTypes.add(kwargsType);
                        actualTypes.add(actualKeywordParam.getArgumentType(context));
                        expectedParameters.pop();
                    }
                    else {
                        // Keyword container must be present in the actual signature
                        return null;
                    }
                }
                // TypeVarTuple (*Ts) consumes a calculated number of positional parameters
                case TYPE_VAR_TUPLE -> {
                    expectedTypes.add(expectedParameter.getArgumentType(context));

                    for (long i = 0; i < paramsTypeVarTupleShouldAccept; i++) {
                        if (actualParameters.isEmpty()) {
                            return null;
                        }
                        Parameter actualTypeVarTupleParameter = actualParameters.pop();
                        if (!actualTypeVarTupleParameter.getAcceptsPositionalArgument() &&
                            actualTypeVarTupleParameter.kind() != ParameterKind.TYPE_VAR_TUPLE) {
                            return null;
                        }
                        PyType actualType = actualTypeVarTupleParameter.getArgumentType(context);
                        // *args: T mapped to TypeVarTuple should be represented as *tuple[T, ...]
                        if (actualTypeVarTupleParameter.parameter().isPositionalContainer() && !(actualType instanceof PyPositionalVariadicType)) {
                            actualType = PyUnpackedTupleTypeImpl.createUnbound(actualType);
                        }
                        actualTypes.add(actualType);
                    }
                    // TypeVarTuple consumed all the required parameters.
                    expectedParameters.pop();
                }
            }
        }
        // Post-process remaining params
        while (!expectedParameters.isEmpty()) {
            Parameter parameter = expectedParameters.pop();
            PyType type = parameter.getArgumentType(context);
            // TODO remove these container checks when type parameters in protocols are properly substituted
            if (parameter.parameter().isPositionalContainer() && (type instanceof PyPositionalVariadicType || type instanceof PyParamSpecType)) {
                continue;
            }
            else if (parameter.parameter().isKeywordContainer() && type instanceof PyParamSpecType) {
                continue;
            }
            else if (parameter.kind() == ParameterKind.TYPE_VAR_TUPLE) { // can be empty
                if (actualTypes.isEmpty()) {
                    expectedTypes.add(type);
                }
            }
            else {
                if (mismatch != null) {
                    mismatch.accept(missingParameter(parameter.getName()));
                }
                return null;
            }
        }
        // Handle unmatched actual parameters (extra in actual)
        while (!actualParameters.isEmpty()) {
            Parameter parameter = actualParameters.pop();
            if (parameter.parameter().isPositionalContainer() || parameter.parameter().isKeywordContainer()) {
                continue;
            }
            else if (parameter.getHasDefault()) {
                continue;
            }
            else {
                if (mismatch != null) {
                    mismatch.accept(unexpectedParameter(parameter.getName()));
                }
                return null;
            }
        }

        return PyTypeParameterMapping.mapByShape(expectedTypes, actualTypes);
    }

    // Localized reasons for the breakdown shown by PyTypeChecker.explainMismatch. Each falls back to the generic
    // "parameter lists are incompatible" line when there is no usable parameter name to point at.
    private static ProblemMessage parameterListIncompatible() {
        return PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterListIncompatible());
    }

    private static ProblemMessage defaultRequired(@Nullable String name) {
        return name != null
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterDefaultRequired(params[0]), name)
            : parameterListIncompatible();
    }

    private static ProblemMessage nameMismatch(@Nullable String expected, @Nullable String actual) {
        return expected != null && actual != null
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterNameMismatch(params[0], params[1]),
                                                  expected, actual)
            : parameterListIncompatible();
    }

    private static ProblemMessage missingParameter(@Nullable String name) {
        return name != null
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterMissing(params[0]), name)
            : parameterListIncompatible();
    }

    private static ProblemMessage unexpectedParameter(@Nullable String name) {
        return name != null
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterUnexpected(params[0]), name)
            : parameterListIncompatible();
    }

    private static ProblemMessage incompatibleKind(@Nullable String name) {
        return name != null
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerBreakdownParameterKindIncompatible(params[0]), name)
            : parameterListIncompatible();
    }

    /**
     * Special handling for keyword containers annotated with {@code Unpacked[TypedDict]} type:
     * <p>
     * We must check if the actual input signature also contains a keyword container
     * before unwrapping TypedDict from the expected signature into a series of keyword-only parameters.
     * See the <a href="https://typing.python.org/en/latest/spec/callables.html#source-contains-kwargs-and-destination-doesn-t">spec</a>
     */
    private static boolean handleUnwrappedTypedDict(List<PyCallableParameter> expectedCallableParameters,
                                                    List<PyCallableParameter> actualCallableParameters,
                                                    TypeEvalContext context) {
        PyCallableParameter expectedLast = expectedCallableParameters.isEmpty() ? null : expectedCallableParameters.get(expectedCallableParameters.size() - 1);
        boolean expectedHasUnpackedTD = expectedLast != null &&
            expectedLast.isKeywordContainer() && expectedLast.getType(context) instanceof PyUnpackedTypedDictType;
        PyCallableParameter actualLast = actualCallableParameters.isEmpty() ? null : actualCallableParameters.get(actualCallableParameters.size() - 1);
        boolean actualHasKeywordContainer = actualLast != null && actualLast.isKeywordContainer();

        return !(expectedHasUnpackedTD && !actualHasKeywordContainer);
    }

    private enum ParameterState {
        POSITIONAL_OR_KEYWORD,
        KEYWORD_ONLY,
        POSITIONAL_CONTAINER,
    }

    /**
     * Categorizes parameters into based on Python's parameter syntax.
     * Returns null if the parameter list is invalid (e.g., duplicate *args or **kwargs).
     */
    private static @Nullable List<Parameter> categorizeParameters(List<PyCallableParameter> callableParameters, TypeEvalContext context) {
        List<Parameter> parameters = new ArrayList<>();
        List<PyCallableParameter> unwrappedParameters = ParamHelper.unpackContainerParameters(callableParameters, context);

        Parameter positionalContainer = null;
        Parameter keywordContainer = null;
        PyTypeVarTupleType typeVarTupleType = null;

        ParameterState state = ParameterState.POSITIONAL_OR_KEYWORD;
        for (PyCallableParameter param : unwrappedParameters) {
            PyType argumentType = param.getArgumentType(context);
            if (param.isPositionOnlySeparator()) {
                parameters.replaceAll(p -> p.copy(ParameterKind.POSITIONAL_ONLY));
                continue;
            }
            else if (param.isKeywordContainer()) {
                if (keywordContainer != null) {
                    return null;
                }

                keywordContainer = new Parameter(param, ParameterKind.KEYWORD_CONTAINER);
                parameters.add(keywordContainer);
                continue;
            }
            else if (param.isPositionalContainer()) {
                if (positionalContainer != null) {
                    return null;
                }

                positionalContainer = new Parameter(param, ParameterKind.POSITIONAL_CONTAINER);
                parameters.add(positionalContainer);
                state = ParameterState.POSITIONAL_CONTAINER;
                continue;
            }
            if (argumentType instanceof PyTypeVarTupleType argumentTypeVarTuple) {
                if (typeVarTupleType != null) {
                    return null; // Only one TypeVarTuple is allowed
                }

                typeVarTupleType = argumentTypeVarTuple;
                parameters.add(new Parameter(param, ParameterKind.TYPE_VAR_TUPLE));
                continue;
            }
            else if (param.isKeywordOnlySeparator()) {
                state = ParameterState.KEYWORD_ONLY;
                continue;
            }
            else {
                if (state == ParameterState.POSITIONAL_OR_KEYWORD) {
                    String paramName = param.getName();
                    if (paramName == null || param.getProtectionLevel() == ProtectionLevel.PRIVATE) {
                        parameters.add(new Parameter(param, ParameterKind.POSITIONAL_ONLY));
                    }
                    else {
                        parameters.add(new Parameter(param, ParameterKind.POSITIONAL_OR_KEYWORD));
                    }
                }
                else {
                    String name = param.getName();
                    if (name == null) {
                        return null;
                    }
                    if (state == ParameterState.POSITIONAL_CONTAINER && param.getProtectionLevel() == ProtectionLevel.PRIVATE) {
                        parameters.add(new Parameter(param, ParameterKind.POSITIONAL_ONLY));
                    }
                    else {
                        parameters.add(new Parameter(param, ParameterKind.KEYWORD_ONLY));
                    }
                }
            }
        }
        return parameters;
    }
}
