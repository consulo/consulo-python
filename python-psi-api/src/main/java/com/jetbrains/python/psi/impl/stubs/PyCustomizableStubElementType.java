// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyElement;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public interface PyCustomizableStubElementType<Psi extends PyElement, Stub extends PyCustomStub, StubType extends PyCustomStubType<Psi, ? extends Stub>> {
    List<StubType> getExtensions();

    default @Nullable Stub createCustomStub(Psi psi) {
        for (StubType type : getExtensions()) {
            final Stub stub = type.createStub(psi);
            if (stub != null) {
                return stub;
            }
        }

        return null;
    }

    default void serializeCustomStub(@Nullable Stub stub, StubOutputStream stream) throws IOException {
        final boolean hasCustomStub = stub != null;
        stream.writeBoolean(hasCustomStub);

        if (hasCustomStub) {
            stream.writeName(stub.getTypeClass().getCanonicalName());
            stub.serialize(stream);
        }
    }

    default @Nullable Stub deserializeCustomStub(StubInputStream stream) throws IOException {
        if (stream.readBoolean()) {
            final String typeName = stream.readNameString();
            for (StubType type : getExtensions()) {
                if (type.getClass().getCanonicalName().equals(typeName)) {
                    return type.deserializeStub(stream);
                }
            }
            throw new IOException("Unknown custom stub type " + typeName);
        }

        return null;
    }
}
