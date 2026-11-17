// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassStubImpl;
import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.Trinity;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyDataclassParametersProvider {
    ExtensionPointName<PyDataclassParametersProvider> EP_NAME = ExtensionPointName.create(PyDataclassParametersProvider.class);

    PyDataclassParameters.Type getType();

    default @Nullable Trinity<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>> getDecoratorAndTypeAndParameters(Project project) {
        return null;
    }

    /**
     * @deprecated Use {@link #buildDataclassStub(PyClass, TypeEvalContext)} instead.
     */
    @Deprecated
    default @Nullable PyDataclassParameters getDataclassParameters(PyClass cls, @Nullable TypeEvalContext context) {
        return null;
    }

    @SuppressWarnings("deprecation")
    default @Nullable Pair<PyDataclassStub, DataclassParameterArgumentMapping> buildDataclassStub(PyClass cls,
                                                                                                 @Nullable TypeEvalContext context) {
        PyDataclassParameters provided = getDataclassParameters(cls, context);
        if (provided == null) {
            return null;
        }
        return Pair.create(
            PyDataclassStubImpl.of(
                provided.getType(),
                null,
                provided.getInit(),
                provided.getRepr(),
                provided.getEq(),
                provided.getOrder(),
                provided.getUnsafeHash(),
                provided.getFrozen(),
                provided.getMatchArgs(),
                provided.getKwOnly(),
                provided.getSlots()
            ),
            new DataclassParameterArgumentMapping(
                provided.getInitArgument(),
                provided.getReprArgument(),
                provided.getEqArgument(),
                provided.getOrderArgument(),
                provided.getUnsafeHashArgument(),
                provided.getFrozenArgument(),
                provided.getMatchArgsArgument(),
                provided.getKwOnlyArgument(),
                provided.getSlotsArgument(),
                provided.getOthers()
            )
        );
    }

    default @Nullable PyDataclassFieldStub buildDataclassFieldStub(PyTargetExpression expression) {
        return null;
    }
}
