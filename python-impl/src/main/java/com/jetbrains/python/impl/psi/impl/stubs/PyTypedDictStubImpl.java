// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyTypedDictFieldStub;
import com.jetbrains.python.impl.psi.stubs.PyTypedDictStub;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_CLOSED_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_EXTRA_ITEMS_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_TOTAL_PARAMETER;

public class PyTypedDictStubImpl implements PyTypedDictStub {
    private final QualifiedName myCalleeName;
    private final String name;
    private final List<PyTypedDictFieldStub> fields;
    private final boolean isRequired;
    private final boolean isClosed;
    private final @Nullable String extraItemsType;

    private PyTypedDictStubImpl(QualifiedName calleeName, String name, List<PyTypedDictFieldStub> fields) {
        this(calleeName, name, fields, true, false, null);
    }

    private PyTypedDictStubImpl(QualifiedName calleeName,
                                String name,
                                List<PyTypedDictFieldStub> fields,
                                boolean isRequired,
                                boolean isClosed,
                                @Nullable String extraItemsType) {
        myCalleeName = calleeName;
        this.name = name;
        this.fields = fields;
        this.isRequired = isRequired;
        this.isClosed = isClosed;
        this.extraItemsType = extraItemsType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<PyTypedDictFieldStub> getFields() {
        return fields;
    }

    @Override
    public boolean isRequired() {
        return isRequired;
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public @Nullable String getExtraItemsType() {
        return extraItemsType;
    }

    @Override
    public Class<PyTypedDictStubType> getTypeClass() {
        return PyTypedDictStubType.class;
    }

    @Override
    public void serialize(StubOutputStream stream) throws IOException {
        stream.writeName(myCalleeName.toString());
        stream.writeName(name);
        stream.writeBoolean(isRequired);
        stream.writeBoolean(isClosed);
        stream.writeName(extraItemsType);
        stream.writeVarInt(fields.size());

        for (PyTypedDictFieldStub field : fields) {
            stream.writeName(field.name());
            stream.writeName(field.type());
            stream.writeBoolean(field.isReadOnly());
        }
    }

    @Override
    public QualifiedName getCalleeName() {
        return myCalleeName;
    }

    @Override
    public String toString() {
        return "PyTypedDictStub(calleeName=" + myCalleeName + ", name=" + name + ", fields=" + fields + ", isRequired=" + isRequired + ")";
    }

    public static @Nullable PyTypedDictStub create(PyTargetExpression expression) {
        PyExpression assignedValue = expression.findAssignedValue();

        return assignedValue instanceof PyCallExpression callExpression ? create(callExpression) : null;
    }

    private static @Nullable PyTypedDictStub create(PyCallExpression expression) {
        if (!(expression.getCallee() instanceof PyReferenceExpression calleeReference)) {
            return null;
        }
        QualifiedName calleeName = getCalleeName(calleeReference);
        if (calleeName == null) {
            return null;
        }

        PyExpression[] arguments = expression.getArguments();
        String typeName = PyResolveUtil.resolveStrArgument(arguments.length > 0 ? arguments[0] : null);
        if (typeName == null) {
            return null;
        }

        PyExpression fieldsArg = PyPsiUtils.flattenParens(arguments.length > 1 ? arguments[1] : null);
        List<PyTypedDictFieldStub> fields = fieldsArg instanceof PyDictLiteralExpression dictLiteral
                                            ? getTypedDictFieldsFromDictLiteral(dictLiteral)
                                            : Collections.emptyList();

        PyExpression extraItems = expression.getKeywordArgument(TYPED_DICT_EXTRA_ITEMS_PARAMETER);
        return new PyTypedDictStubImpl(calleeName,
                                       typeName,
                                       fields,
                                       PyEvaluator.evaluateAsBooleanNoResolve(expression.getKeywordArgument(TYPED_DICT_TOTAL_PARAMETER), true),
                                       PyEvaluator.evaluateAsBooleanNoResolve(expression.getKeywordArgument(TYPED_DICT_CLOSED_PARAMETER), false),
                                       extraItems != null ? extraItems.getText() : null);
    }

    public static @Nullable PyTypedDictStub deserialize(StubInputStream stream) throws IOException {
        String calleeName = stream.readNameString();
        String name = stream.readNameString();
        boolean isRequired = stream.readBoolean();
        boolean isClosed = stream.readBoolean();
        String extraItemsType = stream.readNameString();
        List<PyTypedDictFieldStub> fields = deserializeFields(stream, stream.readVarInt());

        if (calleeName == null || name == null) {
            return null;
        }
        else {
            return new PyTypedDictStubImpl(QualifiedName.fromDottedString(calleeName), name, fields, isRequired, isClosed, extraItemsType);
        }
    }

    private static @Nullable QualifiedName getCalleeName(PyReferenceExpression referenceExpression) {
        QualifiedName calleeName = PyPsiUtils.asQualifiedName(referenceExpression);
        if (calleeName == null) {
            return null;
        }

        for (QualifiedName qName : PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression)) {
            String name = qName.toString();
            if (PyTypedDictTypeProvider.Helper.nameIsTypedDict(name)) {
                return calleeName;
            }
        }

        return null;
    }

    private static List<PyTypedDictFieldStub> deserializeFields(StubInputStream stream, int fieldsCount) throws IOException {
        List<PyTypedDictFieldStub> fields = new ArrayList<>(fieldsCount);

        for (int i = 0; i < fieldsCount; i++) {
            String name = stream.readNameString();
            String type = stream.readNameString();
            boolean readOnly = stream.readBoolean();

            if (name != null) {
                fields.add(new PyTypedDictFieldStub(name, type, readOnly));
            }
        }

        return fields;
    }

    private static List<PyTypedDictFieldStub> getTypedDictFieldsFromDictLiteral(PyDictLiteralExpression expression) {
        List<PyTypedDictFieldStub> result = new ArrayList<>();
        for (PyKeyValueExpression it : expression.getElements()) {
            PyExpression key = it.getKey();
            if (key instanceof PyStringLiteralExpression stringLiteral) {
                PyExpression value = it.getValue();
                result.add(new PyTypedDictFieldStub(stringLiteral.getStringValue(), value != null ? value.getText() : null, true));
            }
        }
        return result;
    }
}
