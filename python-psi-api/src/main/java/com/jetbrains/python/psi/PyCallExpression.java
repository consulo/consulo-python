// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.ast.PyAstCallExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents an entire call expression, like <tt>foo()</tt> or <tt>foo.bar[1]('x')</tt>.
 */
public interface PyCallExpression extends PyAstCallExpression, PyCallSiteExpression {

    /**
     * @return the expression representing the object being called (reference to a function).
     */
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getCallee() {
        return (PyExpression)PyAstCallExpression.super.getCallee();
    }

    /**
     * @return the argument list used in the call.
     */
    @Override
    @RequiredReadAction
    default @Nullable PyArgumentList getArgumentList() {
        return (PyArgumentList)PyAstCallExpression.super.getArgumentList();
    }

    /**
     * @return the argument array used in the call, or an empty array if the call has no argument list.
     */
    @Override
    @RequiredReadAction
    default PyExpression[] getArguments() {
        PyArgumentList argList = getArgumentList();
        return argList != null ? argList.getArguments() : PyExpression.EMPTY_ARRAY;
    }

    /**
     * Returns the argument if one is present in the list and has appropriate type.
     *
     * @param parameter parameter
     * @param argClass  argument expected type
     * @return the argument expression or null if has wrong type or does not exist
     */
    @RequiredReadAction
    default <T extends PsiElement> @Nullable T getArgument(FunctionParameter parameter, Class<T> argClass) {
        PyArgumentList list = getArgumentList();
        if (list == null) {
            return null;
        }
        return ObjectUtil.tryCast(list.getValueExpressionForParam(parameter), argClass);
    }

    /**
     * Returns the argument marked with the specified keyword, if one is present in the list.
     *
     * @param keyword argument keyword
     * @return the argument or null
     */
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getKeywordArgument(String keyword) {
        return (PyExpression)PyAstCallExpression.super.getKeywordArgument(keyword);
    }

    /**
     * Resolves the callee to possible functions.
     * Try to use {@link PyCallExpression#multiResolveCallee(PyResolveContext)}
     * because resolve result could contain {@code null} callable but {@code non-null} callable type.
     *
     * @param resolveContext resolve context
     * @return the resolved callees or an empty list.
     */
    default List<PyCallable> multiResolveCalleeFunction(PyResolveContext resolveContext) {
        return ContainerUtil.mapNotNull(multiResolveCallee(resolveContext), PyCallableType::getCallable);
    }

    /**
     * Resolves the callee to the individual callable signatures it may denote: a union of callables yields one element per
     * member, and an overloaded callable is unfolded into its overloads. The union/intersection structure of the callee
     * type is not preserved here; resolution that keeps it lives in the PSI implementation's {@code getCalleeType}.
     *
     * @param resolveContext resolve context
     * @return the resolved callee callable types.
     */
    List<PyCallableType> multiResolveCallee(PyResolveContext resolveContext);

    /**
     * Resolves the callee to possible functions and maps arguments to parameters for all of them.
     *
     * @param resolveContext resolve context
     * @return objects which contains callable and mappings.
     * Returned list is empty if the callee couldn't be resolved.
     */
    List<PyArgumentsMapping> multiMapArguments(PyResolveContext resolveContext);

    class PyArgumentsMapping {
        private final PyElement myCallSite;
        private final List<PyExpression> myArguments;
        private final @Nullable PyCallableType myCallableType;
        private final Map<PyExpression, PyCallableParameter> myMappedParameters;
        private final List<PyCallableParameter> myUnmappedParameters;
        private final List<PyCallableParameter> myUnmappedContainerParameters;
        private final List<PyExpression> myUnmappedArguments;
        private final List<PyCallableParameter> myParametersMappedToVariadicPositionalArguments;
        private final List<PyCallableParameter> myParametersMappedToVariadicKeywordArguments;
        private final Map<PyExpression, PyCallableParameter> myMappedTupleParameters;

        public PyArgumentsMapping(PyElement callSite,
                                  List<PyExpression> arguments,
                                  @Nullable PyCallableType callableType,
                                  Map<PyExpression, PyCallableParameter> mappedParameters,
                                  List<PyCallableParameter> unmappedParameters,
                                  List<PyCallableParameter> unmappedContainerParameters,
                                  List<PyExpression> unmappedArguments,
                                  List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                                  List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                                  Map<PyExpression, PyCallableParameter> tupleMappedParameters) {
            myCallSite = callSite;
            myArguments = arguments;
            myCallableType = callableType;
            myMappedParameters = mappedParameters;
            myUnmappedParameters = unmappedParameters;
            myUnmappedContainerParameters = unmappedContainerParameters;
            myUnmappedArguments = unmappedArguments;
            myParametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
            myParametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
            myMappedTupleParameters = tupleMappedParameters;
        }

        public static PyArgumentsMapping empty(PyElement callSiteExpression,
                                               List<PyExpression> arguments) {
            return new PyCallExpression.PyArgumentsMapping(callSiteExpression,
                arguments,
                null,
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap());
        }

        public PyElement getCallSite() {
            return myCallSite;
        }

        public List<PyExpression> getArguments() {
            return myArguments;
        }

        public @Nullable PyCallableType getCallableType() {
            return myCallableType;
        }

        public Map<PyExpression, PyCallableParameter> getMappedParameters() {
            return myMappedParameters;
        }

        public List<PyCallableParameter> getUnmappedParameters() {
            return myUnmappedParameters;
        }

        public List<PyCallableParameter> getUnmappedContainerParameters() {
            return myUnmappedContainerParameters;
        }

        public List<PyExpression> getUnmappedArguments() {
            return myUnmappedArguments;
        }

        public List<PyCallableParameter> getParametersMappedToVariadicPositionalArguments() {
            return myParametersMappedToVariadicPositionalArguments;
        }

        public List<PyCallableParameter> getParametersMappedToVariadicKeywordArguments() {
            return myParametersMappedToVariadicKeywordArguments;
        }

        public Map<PyExpression, PyCallableParameter> getMappedTupleParameters() {
            return myMappedTupleParameters;
        }

        /**
         * @return true if there are no unmapped parameters and no unmapped arguments, false otherwise
         */
        public boolean isComplete() {
            return getUnmappedParameters().isEmpty() && getUnmappedArguments().isEmpty();
        }
    }
}
