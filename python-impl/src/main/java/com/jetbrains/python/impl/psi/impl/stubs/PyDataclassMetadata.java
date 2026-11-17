// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.util.lang.function.ThrowableConsumer;
import consulo.util.lang.function.ThrowableFunction;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * An opaque, framework-specific payload persisted in a dataclass class stub ({@link com.jetbrains.python.impl.psi.stubs.PyDataclassStub}) or field stub
 * ({@link com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub}).
 */
public final class PyDataclassMetadata {
    private final byte[] bytes;

    private PyDataclassMetadata(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Decodes the payload with the framework's own reader, or returns {@code null} if the payload could not be read
     * (truncated or written by an incompatible version).
     */
    public <T> @Nullable T decode(ThrowableFunction<DataInput, T, IOException> reader) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return reader.apply(input);
        }
        catch (IOException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "PyDataclassMetadata(" + bytes.length + " bytes)";
    }

    public static PyDataclassMetadata encode(ThrowableConsumer<DataOutput, IOException> writer) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            writer.consume(output);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new PyDataclassMetadata(buffer.toByteArray());
    }

    public static @Nullable PyDataclassMetadata readFrom(StubInputStream stream) throws IOException {
        return DataInputOutputUtil.readNullable(stream, () -> {
            byte[] bytes = new byte[DataInputOutputUtil.readINT(stream)];
            stream.readFully(bytes);
            return new PyDataclassMetadata(bytes);
        });
    }

    public static void writeTo(StubOutputStream stream, @Nullable PyDataclassMetadata metadata) throws IOException {
        DataInputOutputUtil.writeNullable(stream, metadata, it -> {
            DataInputOutputUtil.writeINT(stream, it.bytes.length);
            stream.write(it.bytes);
        });
    }
}
