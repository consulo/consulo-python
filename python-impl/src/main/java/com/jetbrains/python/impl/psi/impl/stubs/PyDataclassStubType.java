// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.stubs.PyCustomClassStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * The single {@link PyCustomClassStubType} for every dataclass framework ({@code Pythonid.customClassStubType}).
 * <p>
 * Detection has one source of truth: {@link PyDataclassesKt#parseDataclassParametersForStub}, which chains the per-framework detectors in
 * priority order and produces the {@link PyDataclassStub} with the matching framework {@link PyDataclassStub#getType()}. The stub is
 * (de)serialized by {@link PyDataclassStubImpl}.
 */
@ExtensionImpl
public class PyDataclassStubType extends PyCustomClassStubType<PyDataclassStub> {
    @Override
    public @Nullable PyDataclassStub createStub(PyClass psi) {
        return PyDataclassesKt.parseDataclassParametersForStub(psi);
    }

    @Override
    public PyDataclassStub deserializeStub(StubInputStream stream) throws IOException {
        return PyDataclassStubImpl.deserialize(stream);
    }
}
