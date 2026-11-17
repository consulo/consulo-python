// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.refactoring.suggested;

import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.refactoring.changeSignature.PyChangeSignatureProcessor;
import com.jetbrains.python.impl.refactoring.changeSignature.PyParameterInfo;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.suggested.SuggestedChangeSignatureData;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringExecution;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringSupport.Parameter;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

class PySuggestedRefactoringExecution extends SuggestedRefactoringExecution {
    PySuggestedRefactoringExecution(PySuggestedRefactoringSupport support) {
        super(support);
    }

    @Override
    public @Nullable Object prepareChangeSignature(SuggestedChangeSignatureData data) {
        return null;
    }

    @Override
    @RequiredReadAction
    public void performChangeSignature(
        SuggestedChangeSignatureData data,
        List<NewParameterValue> newParameterValues,
        @Nullable Object preparedData
    ) {
        Iterator<NewParameterValue> newParameterValuesIterator = newParameterValues.iterator();

        List<PyParameterInfo> parameterInfo = new ArrayList<>();
        for (Parameter newParameter : data.getNewSignature().getParameters()) {
            Parameter oldParameter = data.getOldSignature().parameterById(newParameter.getId());

            int oldIndex = oldParameter == null ? -1 : data.getOldSignature().parameterIndex(oldParameter);

            String oldName = oldParameter != null ? oldParameter.getName() : "";
            String newName = newParameter.getName();

            Pair<String, Boolean> defaultValueInfo = defaultValueInfo(newParameter, oldParameter, newParameterValuesIterator);

            PyParameterInfo info = new PyParameterInfo(oldIndex, oldName, defaultValueInfo.getFirst(), defaultValueInfo.getSecond());
            info.setName(newName);
            parameterInfo.add(info);
        }

        PyFunction function = (PyFunction) data.getDeclaration();
        new PyChangeSignatureProcessor(
            function.getProject(),
            function,
            data.getNewSignature().getName(),
            parameterInfo.toArray(new PyParameterInfo[0])
        ).run();
    }

    private static Pair<String, Boolean> defaultValueInfo(
        Parameter newParameter,
        @Nullable Parameter oldParameter,
        Iterator<NewParameterValue> newParametersValues
    ) {
        if (!ParamHelper.couldHaveDefaultValue(newParameter.getName())) {
            return Pair.create(null, false);
        }

        String newDefaultValueInSignature = PySuggestedRefactoringSupport.Helper.defaultValue(newParameter);

        String defaultValue = defaultValueForProcessor(
            newDefaultValueInSignature,
            oldParameter != null ? PySuggestedRefactoringSupport.Helper.defaultValue(oldParameter) : null,
            oldParameter == null,
            newParametersValues
        );
        if (defaultValue == null) {
            return Pair.create(null, false);
        }

        return Pair.create(defaultValue, Objects.equals(newDefaultValueInSignature, defaultValue));
    }

    private static @Nullable String defaultValueForProcessor(
        @Nullable String newDefaultValueInSignature,
        @Nullable String oldDefaultValueInSignature,
        boolean added,
        Iterator<NewParameterValue> newParametersValues
    ) {
        if (added) {
            String onCallSite = defaultValueOnCallSite(newParametersValues);
            return onCallSite != null ? onCallSite : newDefaultValueInSignature;
        }
        else if (newDefaultValueInSignature == null && oldDefaultValueInSignature != null) {
            String onCallSite = defaultValueOnCallSite(newParametersValues);
            return onCallSite != null ? onCallSite : oldDefaultValueInSignature;
        }
        else {
            return newDefaultValueInSignature != null ? newDefaultValueInSignature : oldDefaultValueInSignature;
        }
    }

    private static @Nullable String defaultValueOnCallSite(Iterator<NewParameterValue> newParametersValues) {
        if (!(newParametersValues.next() instanceof NewParameterValue.Expression expression)) {
            return null;
        }
        return expression.expression().getText();
    }
}
