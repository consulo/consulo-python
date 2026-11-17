// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;

import java.util.List;
import java.util.Map;

public class ArgumentMappingResults {
    private final Map<PyCallableArgument, PyCallableParameter> mappedParameters;
    private final List<PyCallableParameter> unmappedParameters;
    private final List<PyCallableParameter> unmappedContainerParameters;
    private final List<PyCallableArgument> unmappedArguments;
    private final List<PyCallableParameter> parametersMappedToVariadicPositionalArguments;
    private final List<PyCallableParameter> parametersMappedToVariadicKeywordArguments;
    private final Map<PyCallableArgument, PyCallableParameter> mappedTupleParameters;

    ArgumentMappingResults(Map<PyCallableArgument, PyCallableParameter> mappedParameters,
                           List<PyCallableParameter> unmappedParameters,
                           List<PyCallableParameter> unmappedContainerParameters,
                           List<PyCallableArgument> unmappedArguments,
                           List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                           List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                           Map<PyCallableArgument, PyCallableParameter> mappedTupleParameters) {
        this.mappedParameters = mappedParameters;
        this.unmappedParameters = unmappedParameters;
        this.unmappedContainerParameters = unmappedContainerParameters;
        this.unmappedArguments = unmappedArguments;
        this.parametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
        this.parametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
        this.mappedTupleParameters = mappedTupleParameters;
    }

    public Map<PyCallableArgument, PyCallableParameter> getMappedParameters() {
        return mappedParameters;
    }

    public List<PyCallableParameter> getUnmappedParameters() {
        return unmappedParameters;
    }

    public List<PyCallableParameter> getUnmappedContainerParameters() {
        return unmappedContainerParameters;
    }

    public List<PyCallableArgument> getUnmappedArguments() {
        return unmappedArguments;
    }

    public List<PyCallableParameter> getParametersMappedToVariadicPositionalArguments() {
        return parametersMappedToVariadicPositionalArguments;
    }

    public List<PyCallableParameter> getParametersMappedToVariadicKeywordArguments() {
        return parametersMappedToVariadicKeywordArguments;
    }

    public Map<PyCallableArgument, PyCallableParameter> getMappedTupleParameters() {
        return mappedTupleParameters;
    }
}
