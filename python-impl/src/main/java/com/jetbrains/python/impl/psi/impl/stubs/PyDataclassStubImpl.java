// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.psi.PyClass;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Implementation of {@link PyDataclassStub} shared by every dataclass framework.
 * <p>
 * The dataclass parameters common to every framework (decorator name + the nine boolean flags) are (de)serialized
 * through {@link #serializeCommon}/{@link #readCommon}. Besides those it persists the framework {@link #getType() type} and an opaque
 * {@link #getMetadata() metadata} payload so a framework can round-trip its own configuration (e.g. Pydantic's {@code populate_by_name} /
 * {@code validate_by_name} / {@code validate_by_alias}) without core knowing the framework. Instances are built by {@link #of} and
 * (de)serialized by the single {@link PyDataclassStubType}.
 */
public class PyDataclassStubImpl implements PyDataclassStub {
    private final String type;
    private final @Nullable QualifiedName decoratorName;
    private final @Nullable Boolean init;
    private final @Nullable Boolean repr;
    private final @Nullable Boolean eq;
    private final @Nullable Boolean order;
    private final @Nullable Boolean unsafeHash;
    private final @Nullable Boolean frozen;
    private final @Nullable Boolean matchArgs;
    private final @Nullable Boolean kwOnly;
    private final @Nullable Boolean slots;
    private final @Nullable PyDataclassMetadata metadata;

    public PyDataclassStubImpl(String type,
                               @Nullable QualifiedName decoratorName,
                               @Nullable Boolean init,
                               @Nullable Boolean repr,
                               @Nullable Boolean eq,
                               @Nullable Boolean order,
                               @Nullable Boolean unsafeHash,
                               @Nullable Boolean frozen,
                               @Nullable Boolean matchArgs,
                               @Nullable Boolean kwOnly,
                               @Nullable Boolean slots) {
        this(type, decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots, null);
    }

    public PyDataclassStubImpl(String type,
                               @Nullable QualifiedName decoratorName,
                               @Nullable Boolean init,
                               @Nullable Boolean repr,
                               @Nullable Boolean eq,
                               @Nullable Boolean order,
                               @Nullable Boolean unsafeHash,
                               @Nullable Boolean frozen,
                               @Nullable Boolean matchArgs,
                               @Nullable Boolean kwOnly,
                               @Nullable Boolean slots,
                               @Nullable PyDataclassMetadata metadata) {
        this.type = type;
        this.decoratorName = decoratorName;
        this.init = init;
        this.repr = repr;
        this.eq = eq;
        this.order = order;
        this.unsafeHash = unsafeHash;
        this.frozen = frozen;
        this.matchArgs = matchArgs;
        this.kwOnly = kwOnly;
        this.slots = slots;
        this.metadata = metadata;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public @Nullable PyDataclassMetadata getMetadata() {
        return metadata;
    }

    @Override
    public Class<PyDataclassStubType> getTypeClass() {
        return PyDataclassStubType.class;
    }

    public static @Nullable PyDataclassStub create(PyClass cls) {
        return PyDataclassesKt.parseDataclassParametersForStub(cls);
    }

    public static PyDataclassStub of(PyDataclassParameters.Type type,
                                     @Nullable QualifiedName decoratorName,
                                     @Nullable Boolean init,
                                     @Nullable Boolean repr,
                                     @Nullable Boolean eq,
                                     @Nullable Boolean order,
                                     @Nullable Boolean unsafeHash,
                                     @Nullable Boolean frozen,
                                     @Nullable Boolean matchArgs,
                                     @Nullable Boolean kwOnly,
                                     @Nullable Boolean slots) {
        return of(type, decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots, null);
    }

    /**
     * Builds the dataclass stub for the given parsed dataclass parameters. The {@code type} discriminator records the
     * framework; any framework-specific configuration rides along in the opaque {@code metadata} payload.
     */
    public static PyDataclassStub of(PyDataclassParameters.Type type,
                                     @Nullable QualifiedName decoratorName,
                                     @Nullable Boolean init,
                                     @Nullable Boolean repr,
                                     @Nullable Boolean eq,
                                     @Nullable Boolean order,
                                     @Nullable Boolean unsafeHash,
                                     @Nullable Boolean frozen,
                                     @Nullable Boolean matchArgs,
                                     @Nullable Boolean kwOnly,
                                     @Nullable Boolean slots,
                                     @Nullable PyDataclassMetadata metadata) {
        return new PyDataclassStubImpl(type.getName(), decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots,
                                       metadata);
    }

    public static PyDataclassStub deserialize(StubInputStream stream) throws IOException {
        return readCommon(stream, (decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots) -> {
            String type = StringUtil.notNullize(stream.readNameString());
            PyDataclassMetadata metadata = PyDataclassMetadata.readFrom(stream);
            return new PyDataclassStubImpl(
                type, decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots, metadata
            );
        });
    }

    /**
     * Factory constructing the desired stub-specific object from the payload shared by every framework.
     */
    @FunctionalInterface
    public interface CommonFactory<S> {
        S create(@Nullable QualifiedName decoratorName,
                 @Nullable Boolean init,
                 @Nullable Boolean repr,
                 @Nullable Boolean eq,
                 @Nullable Boolean order,
                 @Nullable Boolean unsafeHash,
                 @Nullable Boolean frozen,
                 @Nullable Boolean matchArgs,
                 @Nullable Boolean kwOnly,
                 @Nullable Boolean slots) throws IOException;
    }

    /**
     * The dataclass parameters shared by every framework: decorator name + the nine boolean flags.
     * <p>
     * The caller passes a factory to construct the desired stub-specific object from the shared payload.
     */
    public static <S> S readCommon(StubInputStream stream, CommonFactory<S> factory) throws IOException {
        QualifiedName decoratorName = QualifiedName.deserialize(stream);
        Boolean init = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean repr = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean eq = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean order = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean unsafeHash = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean frozen = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean matchArgs = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean kwOnly = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        Boolean slots = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        return factory.create(decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots);
    }

    @Override
    public void serialize(StubOutputStream stream) throws IOException {
        serializeCommon(stream);
        stream.writeName(type);
        PyDataclassMetadata.writeTo(stream, metadata);
    }

    /**
     * Writes decorator name + the nine optional boolean flags.
     */
    private void serializeCommon(StubOutputStream stream) throws IOException {
        QualifiedName.serialize(decoratorName, stream);
        DataInputOutputUtil.writeNullable(stream, init, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, repr, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, eq, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, order, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, unsafeHash, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, frozen, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, matchArgs, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, kwOnly, stream::writeBoolean);
        DataInputOutputUtil.writeNullable(stream, slots, stream::writeBoolean);
    }

    @Override
    public @Nullable QualifiedName decoratorName() {
        return decoratorName;
    }

    @Override
    public @Nullable Boolean initValue() {
        return init;
    }

    @Override
    public @Nullable Boolean reprValue() {
        return repr;
    }

    @Override
    public @Nullable Boolean eqValue() {
        return eq;
    }

    @Override
    public @Nullable Boolean orderValue() {
        return order;
    }

    @Override
    public @Nullable Boolean unsafeHashValue() {
        return unsafeHash;
    }

    @Override
    public @Nullable Boolean frozenValue() {
        return frozen;
    }

    @Override
    public @Nullable Boolean matchArgsValue() {
        return matchArgs;
    }

    @Override
    public @Nullable Boolean kwOnly() {
        return kwOnly;
    }

    @Override
    public @Nullable Boolean slotsValue() {
        return slots;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
               "type='" + type + "', " +
               "decoratorName=" + decoratorName + ", " +
               "init=" + init + ", " +
               "repr=" + repr + ", " +
               "eq=" + eq + ", " +
               "order=" + order + ", " +
               "unsafeHash=" + unsafeHash + ", " +
               "frozen=" + frozen + ", " +
               "matchArgs=" + matchArgs + ", " +
               "kwOnly=" + kwOnly + ", " +
               "slots=" + slots + ", " +
               "metadata=" + metadata +
               ")";
    }
}
