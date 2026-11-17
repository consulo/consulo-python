// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeCheckerExtension;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@ExtensionImpl
public class PyDataclassInstanceProtocolTypeCheckerExtension implements PyTypeCheckerExtension {
    @Override
    public Optional<Boolean> match(@Nullable PyType expected,
                                   @Nullable PyType actual,
                                   TypeEvalContext context,
                                   PyTypeChecker.GenericSubstitutions substitutions) {
        if (expected instanceof PyClassType expectedClassType &&
            actual instanceof PyClassType actualClassType &&
            "DataclassInstance".equals(expectedClassType.getPyClass().getName()) &&
            PyProtocolsKt.isProtocol(expectedClassType, context)) {
            PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(actualClassType.getPyClass(), context);
            return Optional.of(parameters != null && parameters.getType() == PyStdlibDataclassType.INSTANCE);
        }
        return Optional.empty();
    }
}
