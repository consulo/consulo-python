// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.codeInsight.PyDataclassParametersProvider;
import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Implementation of {@link PyDataclassFieldStub} shared by every dataclass framework.
 * <p>
 * Besides the field parameters common to every framework it persists the framework {@link #getType() type} (matching
 * {@link com.jetbrains.python.impl.psi.stubs.PyDataclassStub#getType()} of the owning class) and an opaque {@link #getMetadata() metadata}
 * payload so a framework can round-trip its own per-field configuration without core knowing the framework.
 */
public class PyDataclassFieldStubImpl implements PyDataclassFieldStub {
    private final String type;
    private final QualifiedName calleeName;
    private final boolean hasDefault;
    private final boolean hasDefaultFactory;
    private final boolean initValue;
    private final @Nullable Boolean kwOnly;
    private final @Nullable String alias;
    private final @Nullable PyDataclassMetadata metadata;

    public PyDataclassFieldStubImpl(String type,
                                    QualifiedName calleeName,
                                    boolean hasDefault,
                                    boolean hasDefaultFactory,
                                    boolean initValue,
                                    @Nullable Boolean kwOnly,
                                    @Nullable String alias) {
        this(type, calleeName, hasDefault, hasDefaultFactory, initValue, kwOnly, alias, null);
    }

    public PyDataclassFieldStubImpl(String type,
                                    QualifiedName calleeName,
                                    boolean hasDefault,
                                    boolean hasDefaultFactory,
                                    boolean initValue,
                                    @Nullable Boolean kwOnly,
                                    @Nullable String alias,
                                    @Nullable PyDataclassMetadata metadata) {
        this.type = type;
        this.calleeName = calleeName;
        this.hasDefault = hasDefault;
        this.hasDefaultFactory = hasDefaultFactory;
        this.initValue = initValue;
        this.kwOnly = kwOnly;
        this.alias = alias;
        this.metadata = metadata;
    }

    @Override
    public Class<PyDataclassFieldStubType> getTypeClass() {
        return PyDataclassFieldStubType.class;
    }

    public static @Nullable PyDataclassFieldStub create(PyTargetExpression expression) {
        return PyDataclassParametersProvider.EP_NAME.computeSafeIfAny(it -> it.buildDataclassFieldStub(expression));
    }

    public static @Nullable PyDataclassFieldStub deserialize(StubInputStream stream) throws IOException {
        String type = StringUtil.notNullize(stream.readNameString());
        String calleeName = stream.readNameString();
        if (calleeName == null) {
            return null;
        }
        boolean hasDefault = stream.readBoolean();
        boolean hasDefaultFactory = stream.readBoolean();
        boolean initValue = stream.readBoolean();
        Boolean kwOnly = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
        String alias = stream.readNameString();
        PyDataclassMetadata metadata = PyDataclassMetadata.readFrom(stream);

        return new PyDataclassFieldStubImpl(
            type,
            QualifiedName.fromDottedString(calleeName),
            hasDefault,
            hasDefaultFactory,
            initValue,
            kwOnly,
            alias,
            metadata
        );
    }

    @Override
    public void serialize(StubOutputStream stream) throws IOException {
        stream.writeName(type);
        stream.writeName(calleeName.toString());
        stream.writeBoolean(hasDefault);
        stream.writeBoolean(hasDefaultFactory);
        stream.writeBoolean(initValue);
        DataInputOutputUtil.writeNullable(stream, kwOnly, stream::writeBoolean);
        stream.writeName(alias);
        PyDataclassMetadata.writeTo(stream, metadata);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public QualifiedName getCalleeName() {
        return calleeName;
    }

    @Override
    public boolean hasDefault() {
        return hasDefault;
    }

    @Override
    public boolean hasDefaultFactory() {
        return hasDefaultFactory;
    }

    @Override
    public boolean initValue() {
        return initValue;
    }

    @Override
    public @Nullable Boolean kwOnly() {
        return kwOnly;
    }

    @Override
    public @Nullable String getAlias() {
        return alias;
    }

    @Override
    public @Nullable PyDataclassMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
               "type='" + type + "', " +
               "calleeName=" + calleeName + ", " +
               "hasDefault=" + hasDefault + ", " +
               "hasDefaultFactory=" + hasDefaultFactory + ", " +
               "initValue=" + initValue + ", " +
               "kwOnly=" + kwOnly + ", " +
               "alias=" + alias + ", " +
               "metadata=" + metadata +
               ")";
    }
}
