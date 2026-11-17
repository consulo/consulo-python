// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.DataclassParameterArgumentMapping;
import com.jetbrains.python.impl.codeInsight.PyDataclassCopyFunction;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

final class PyStdlibDataclassResolver implements PyDataclassResolver {
    public static final PyStdlibDataclassResolver INSTANCE = new PyStdlibDataclassResolver();

    private PyStdlibDataclassResolver() {
    }

    @Override
    public Set<String> getOmittedDefaultQualifiedNames() {
        return Dataclasses.OMITTED_DEFAULTS;
    }

    @Override
    public @Nullable PyDataclassParameters resolveClassParameters(
        PyClass pyClass,
        PyDataclassStub stub,
        PyDataclassParameters.Type type,
        @Nullable DataclassParameterArgumentMapping argumentMapping,
        TypeEvalContext context
    ) {
        if (!stub.getType().equals(PyStdlibDataclassType.INSTANCE.getName())) {
            return null;
        }
        return new PyDataclassParameters(
            orDefault(stub.initValue(), true),
            orDefault(stub.reprValue(), true),
            orDefault(stub.eqValue(), true),
            orDefault(stub.orderValue(), false),
            orDefault(stub.unsafeHashValue(), false),
            orDefault(stub.frozenValue(), false),
            orDefault(stub.matchArgsValue(), true),
            orDefault(stub.kwOnly(), false),
            orDefault(stub.slotsValue(), false),
            argumentMapping != null ? argumentMapping.getInitArgument() : null,
            argumentMapping != null ? argumentMapping.getReprArgument() : null,
            argumentMapping != null ? argumentMapping.getEqArgument() : null,
            argumentMapping != null ? argumentMapping.getOrderArgument() : null,
            argumentMapping != null ? argumentMapping.getUnsafeHashArgument() : null,
            argumentMapping != null ? argumentMapping.getFrozenArgument() : null,
            argumentMapping != null ? argumentMapping.getMatchArgsArgument() : null,
            argumentMapping != null ? argumentMapping.getKwOnlyArgument() : null,
            argumentMapping != null ? argumentMapping.getSlotsArgument() : null,
            type,
            argumentMapping != null ? argumentMapping.getOthers() : Collections.emptyMap(),
            List.of(QualifiedName.fromDottedString(Dataclasses.DATACLASSES_FIELD))
        );
    }

    @Override
    public List<PyDataclassCopyFunction> copyFunctions() {
        return List.of(
            new PyDataclassCopyFunction(QualifiedName.fromDottedString(Dataclasses.DATACLASSES_REPLACE), "obj")
        );
    }

    /**
     * Name of the special post-init hook Python calls after `__init__`.
     */
    @Override
    public String postInitFunctionName() {
        return Dataclasses.DUNDER_POST_INIT;
    }

    private static boolean orDefault(@Nullable Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }
}
