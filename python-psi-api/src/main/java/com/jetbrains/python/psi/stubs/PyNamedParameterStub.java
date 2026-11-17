// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.PyNamedParameter;
import consulo.language.psi.stub.NamedStub;
import org.jspecify.annotations.Nullable;

public interface PyNamedParameterStub extends NamedStub<PyNamedParameter>, PyAnnotationOwnerStub, PyTypeCommentOwnerStub {
    boolean isPositionalContainer();

    boolean isKeywordContainer();

    default @Nullable String getDefaultValueText() {
        return null;
    }
}
