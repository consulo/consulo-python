// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.PyFunction;
import consulo.language.psi.stub.NamedStub;

public interface PyFunctionStub extends NamedStub<PyFunction>, PyAnnotationOwnerStub, PyTypeCommentOwnerStub, PyVersionSpecificStub {
    String getDocString();

    String getDeprecationMessage();

    boolean isAsync();

    boolean isGenerator();

    default boolean onlyRaisesNotImplementedError() {
        return false;
    }
}
