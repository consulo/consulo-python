// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.extractmethod;

import com.jetbrains.python.psi.types.PyType;
import consulo.language.editor.refactoring.extractMethod.ExtractMethodSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// TODO consulo: ExtractMethodSettings<T> (generic, with getVisibility()) missing - Consulo's ExtractMethodSettings is not generic
public class PyExtractMethodSettings implements ExtractMethodSettings {
    private final String myMethodName;
    private final PyVariableData[] myVariableData;
    private final @Nullable String myReturnTypeName;
    private final Set<PyType> myReturnTypes;
    private final boolean myUseTypeAnnotations;

    public PyExtractMethodSettings(String methodName,
                                   PyVariableData[] variableData,
                                   @Nullable String returnTypeName,
                                   Set<PyType> returnTypes,
                                   boolean useTypeAnnotations) {
        myMethodName = methodName;
        myVariableData = variableData;
        myReturnTypeName = returnTypeName;
        myReturnTypes = returnTypes;
        myUseTypeAnnotations = useTypeAnnotations;
    }

    @Override
    public String getMethodName() {
        return myMethodName;
    }

    @Override
    public PyVariableData[] getAbstractVariableData() {
        return myVariableData;
    }

    public @Nullable String getReturnTypeName() {
        return myReturnTypeName;
    }

    public Set<PyType> getReturnTypeFqns() {
        return myReturnTypes;
    }

    public boolean isUseTypeAnnotations() {
        return myUseTypeAnnotations;
    }

    public @Nullable Object getVisibility() {
        return null;
    }

    List<PyType> getAllTypes() {
        List<PyType> result = new ArrayList<>();
        for (PyVariableData variableData : myVariableData) {
            if (variableData.type != null) {
                result.add(variableData.type);
            }
        }
        result.addAll(myReturnTypes);
        return result;
    }
}
