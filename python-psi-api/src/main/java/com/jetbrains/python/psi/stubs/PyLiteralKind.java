// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public enum PyLiteralKind {
    INT,
    FLOAT,
    STRING,
    BOOL,
    NONE;

    public static @Nullable PyLiteralKind fromExpression(@Nullable PyExpression expression) {
        if (expression instanceof PyNumericLiteralExpression numericLiteralExpression) {
            return numericLiteralExpression.isIntegerLiteral() ? INT : FLOAT;
        }
        if (expression instanceof PyStringLiteralExpression) {
            return STRING;
        }
        if (expression instanceof PyBoolLiteralExpression) {
            return BOOL;
        }
        if (expression instanceof PyNoneLiteralExpression) {
            return NONE;
        }
        return null;
    }

    public static void serialize(StubOutputStream stream, @Nullable PyLiteralKind kind) throws IOException {
        int index = kind != null ? kind.ordinal() : -1;
        stream.writeVarInt(index);
    }

    public static @Nullable PyLiteralKind deserialize(StubInputStream stream) throws IOException {
        int index = stream.readVarInt();
        return index == -1 ? null : values()[index];
    }
}
