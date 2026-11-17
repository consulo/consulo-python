// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.impl.psi.stubs.PyTypingAliasStub;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
public class PyTypingTypeAliasStubImpl implements PyTypingAliasStub {
    private final String myText;

    public PyTypingTypeAliasStubImpl(String text) {
        myText = text;
    }

    @Override
    public String getText() {
        return myText;
    }

    @Override
    public Class<PyTypingAliasStubType> getTypeClass() {
        return PyTypingAliasStubType.class;
    }

    @Override
    public void serialize(StubOutputStream stream) throws IOException {
        stream.writeName(myText);
    }

    @Override
    public @Nullable QualifiedName getCalleeName() {
        return null;
    }

    @Override
    public String toString() {
        return "PyTypingAliasStub(text='" + getText() + "')";
    }
}
