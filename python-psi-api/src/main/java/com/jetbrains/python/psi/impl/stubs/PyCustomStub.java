// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import java.io.IOException;

public interface PyCustomStub {

    /**
     * @return type class to distinguish one custom stub from another.
     */
    Class<? extends PyCustomStubType<?, ?>> getTypeClass();

    /**
     * @param stream stream to serialize {@code this} stub
     * @see PyCustomStubType#deserializeStub(StubInputStream)
     */
    void serialize(StubOutputStream stream) throws IOException;
}
