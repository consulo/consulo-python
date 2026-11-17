// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.codeFragment;

import com.jetbrains.python.psi.types.PyType;
import consulo.language.editor.codeFragment.CodeFragment;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class PyCodeFragment extends CodeFragment {
    /**
     * Maps variable names to their type names and types.
     */
    private final Map<String, Pair<String, PyType>> myInputTypes;
    private final @Nullable String myOutputType;
    private final Set<PyType> myOutputTypes;
    private final Set<String> myGlobalWrites;
    private final Set<String> myNonlocalWrites;
    private final boolean myYieldInside;
    private final boolean myAsync;

    public PyCodeFragment(Set<String> input,
                          Set<String> output,
                          Map<String, Pair<String, PyType>> inputTypeNames,
                          @Nullable String outputType,
                          Set<PyType> outputTypes,
                          Set<String> globalWrites,
                          Set<String> nonlocalWrites,
                          boolean returnInside,
                          boolean yieldInside,
                          boolean isAsync) {
        super(input, output, returnInside);
        myInputTypes = inputTypeNames;
        myOutputType = outputType;
        myOutputTypes = outputTypes;
        myGlobalWrites = globalWrites;
        myNonlocalWrites = nonlocalWrites;
        myYieldInside = yieldInside;
        myAsync = isAsync;
    }

    /**
     * Returns the type name of the input variable with the given name.
     */
    public @Nullable String getInputTypeName(String varName) {
        Pair<String, PyType> type = myInputTypes.get(varName);
        return type == null ? null : type.first;
    }

    /**
     * Returns the type of the input variable with the given name.
     */
    public @Nullable PyType getInputType(String varName) {
        Pair<String, PyType> type = myInputTypes.get(varName);
        return type == null ? null : type.second;
    }

    public @Nullable String getOutputType() {
        return myOutputType;
    }

    public Set<PyType> getOutputTypes() {
        return myOutputTypes;
    }

    public Set<String> getGlobalWrites() {
        return myGlobalWrites;
    }

    public Set<String> getNonlocalWrites() {
        return myNonlocalWrites;
    }

    public boolean isYieldInside() {
        return myYieldInside;
    }

    public boolean isAsync() {
        return myAsync;
    }
}
