// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeCheckerExtension;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@ExtensionImpl
public class PyDataclassInitVarTypeCheckerExtension implements PyTypeCheckerExtension {
    @Override
    public Optional<Boolean> match(@Nullable PyType expected,
                                   @Nullable PyType actual,
                                   TypeEvalContext context,
                                   PyTypeChecker.GenericSubstitutions substitutions) {
        if (expected instanceof PyClassType expectedClassType &&
            PyDataclassNames.Dataclasses.DATACLASSES_INITVAR.equals(expectedClassType.getClassQName())) {
            if (expectedClassType.isParameterized()) {
                PyType elementType = ContainerUtil.getFirstItem(expectedClassType.getTypeArguments());
                if (elementType == null) {
                    return Optional.of(true);
                }
                return Optional.of(PyTypeChecker.match(elementType, actual, context, substitutions));
            }
            return Optional.of(true);
        }
        return Optional.empty();
    }
}
