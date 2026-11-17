// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public interface PyCustomStubType<Psi extends PyElement, Stub extends PyCustomStub> {

    /**
     * @param psi element to create stub for
     * @return custom stub for the given psi or null.
     */
    @Nullable
    Stub createStub(Psi psi);

    /**
     * @param stream stream containing serialized stub
     * @return a custom stub instance or null if it could not be read.
     * @see PyCustomStub#serialize(StubOutputStream)
     */
    @Nullable
    Stub deserializeStub(StubInputStream stream) throws IOException;

    default void indexStub(PyClassStub stub, IndexSink sink) { }
}
