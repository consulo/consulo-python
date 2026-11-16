// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.PyTypeParameter;
import consulo.language.psi.stub.NamedStub;
import org.jspecify.annotations.Nullable;

public interface PyTypeParameterStub extends NamedStub<PyTypeParameter> {

    @Nullable
    String getBoundExpressionText();

    @Nullable
    String getDefaultExpressionText();

    PyTypeParameter.Kind getKind();
}
