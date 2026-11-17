// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.DataclassParameterArgumentMapping;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassStubImpl;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

// @ApiStatus.Internal
public class PyDataclassParametersBuilder {
    private final PyDataclassParameters.Type type;
    private final @Nullable QualifiedName decorator;
    private final LinkedHashMap<String, PyExpression> arguments = new LinkedHashMap<>();

    public PyDataclassParametersBuilder(PyDataclassParameters.Type type, @Nullable QualifiedName decorator) {
        this.type = type;
        this.decorator = decorator;
    }

    public void update(@Nullable String name, @Nullable PyExpression argument) {
        if (name != null && argument != null) {
            arguments.put(name, argument);
        }
    }

    public Pair<PyDataclassStub, DataclassParameterArgumentMapping> build() {
        ParametersAccumulator acc = new ParametersAccumulator();

        for (Map.Entry<String, PyExpression> entry : arguments.entrySet()) {
            String name = entry.getKey();
            PyExpression rawArgument = entry.getValue();
            PyExpression value = PyUtil.peelArgument(rawArgument);

            PyDataclassResolver resolver = type.getResolver();
            if (resolver != null && resolver.applyCustomDecoratorParameter(acc, name, value, rawArgument)) {
                continue;
            }
            if (acc.updateCommonParameter(name, value, rawArgument)) {
                continue;
            }

            acc.getFrameworkSpecificArguments().put(name, rawArgument);
        }

        return Pair.create(
            PyDataclassStubImpl.of(
                type,
                decorator,
                acc.getInit(),
                acc.getRepr(),
                acc.getEq(),
                acc.getOrder(),
                acc.getUnsafeHash(),
                acc.getFrozen(),
                acc.getMatchArgs(),
                acc.getKwOnly(),
                acc.getSlots()
            ),
            new DataclassParameterArgumentMapping(
                acc.getInitArgument(),
                acc.getReprArgument(),
                acc.getEqArgument(),
                acc.getOrderArgument(),
                acc.getUnsafeHashArgument(),
                acc.getFrozenArgument(),
                acc.getMatchArgsArgument(),
                acc.getKwOnlyArgument(),
                acc.getSlotsArgument(),
                acc.getFrameworkSpecificArguments()
            )
        );
    }
}
