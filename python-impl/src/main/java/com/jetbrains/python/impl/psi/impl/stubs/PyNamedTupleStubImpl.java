// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyNamedTupleStub;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyPsiUtils.flattenParens;

public final class PyNamedTupleStubImpl implements PyNamedTupleStub {

    private final @Nullable QualifiedName myCalleeName;

    private final String myName;

    private final LinkedHashMap<String, FieldTypeAndHasDefault> myFields;

    private PyNamedTupleStubImpl(@Nullable QualifiedName calleeName,
                                 String name,
                                 LinkedHashMap<String, FieldTypeAndHasDefault> fields) {
        myCalleeName = calleeName;
        myName = name;
        myFields = fields;
    }

    public static @Nullable PyNamedTupleStub create(PyTargetExpression expression) {
        PyExpression assignedValue = expression.findAssignedValue();

        if (assignedValue instanceof PyCallExpression) {
            return create((PyCallExpression)assignedValue);
        }

        return null;
    }

    public static @Nullable PyNamedTupleStub create(PyCallExpression expression) {
        PyReferenceExpression calleeReference = as(expression.getCallee(), PyReferenceExpression.class);

        if (calleeReference == null) {
            return null;
        }

        Pair<QualifiedName, NamedTupleModule> calleeNameAndModule = getCalleeNameAndNTModule(calleeReference);

        if (calleeNameAndModule != null) {
            String name = PyResolveUtil.resolveStrArgument(expression, 0, "typename");

            if (name == null) {
                return null;
            }

            LinkedHashMap<String, FieldTypeAndHasDefault> fields = resolveTupleFields(expression, calleeNameAndModule.getSecond());

            if (fields == null) {
                return null;
            }

            return new PyNamedTupleStubImpl(calleeNameAndModule.getFirst(), name, fields);
        }

        return null;
    }

    public static @Nullable PyNamedTupleStub deserialize(StubInputStream stream) throws IOException {
        String calleeName = stream.readNameString();
        String name = stream.readNameString();
        LinkedHashMap<String, FieldTypeAndHasDefault> fields = deserializeFields(stream, stream.readVarInt());

        if (calleeName == null || name == null) {
            return null;
        }

        return new PyNamedTupleStubImpl(QualifiedName.fromDottedString(calleeName), name, fields);
    }

    @Override
    public Class<PyNamedTupleStubType> getTypeClass() {
        return PyNamedTupleStubType.class;
    }

    @Override
    public void serialize(StubOutputStream stream) throws IOException {
        stream.writeName(myCalleeName == null ? null : myCalleeName.toString());
        stream.writeName(myName);
        stream.writeVarInt(myFields.size());

        for (var entry : myFields.entrySet()) {
            stream.writeName(entry.getKey());
            stream.writeName(entry.getValue().type());
            stream.writeBoolean(entry.getValue().hasDefault());
        }
    }

    @Override
    public @Nullable QualifiedName getCalleeName() {
        return myCalleeName;
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public LinkedHashMap<String, FieldTypeAndHasDefault> getFields() {
        return myFields;
    }

    private static @Nullable Pair<QualifiedName, NamedTupleModule> getCalleeNameAndNTModule(PyReferenceExpression referenceExpression) {
        QualifiedName calleeName = PyPsiUtils.asQualifiedName(referenceExpression);
        if (calleeName == null) {
            return null;
        }

        for (String name : ContainerUtil.map(PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression), QualifiedName::toString)) {
            if (name.equals(PyNames.COLLECTIONS_NAMEDTUPLE_PY2)) {
                return Pair.createNonNull(calleeName, NamedTupleModule.COLLECTIONS);
            }
            else if (name.equals(PyTypingTypeProvider.NAMEDTUPLE)) {
                return Pair.createNonNull(calleeName, NamedTupleModule.TYPING);
            }
        }

        return null;
    }

    private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> resolveTupleFields(PyCallExpression callExpression,
                                                                                              NamedTupleModule module) {
        return switch (module) {
            case TYPING -> resolveTypingNTFields(callExpression);
            case COLLECTIONS -> resolveCollectionsNTFields(callExpression);
        };
    }

    private static LinkedHashMap<String, FieldTypeAndHasDefault> deserializeFields(StubInputStream stream, int fieldsSize)
        throws IOException {
        LinkedHashMap<String, FieldTypeAndHasDefault> fields = new LinkedHashMap<>(fieldsSize);

        for (int i = 0; i < fieldsSize; i++) {
            String name = stream.readNameString();
            String type = stream.readNameString();
            boolean hasDefault = stream.readBoolean();

            fields.put(name, new FieldTypeAndHasDefault(type, hasDefault));
        }

        return fields;
    }

    private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> resolveCollectionsNTFields(PyCallExpression callExpression) {
        // SUPPORTED CASES:

        // fields = ["x", "y"]
        // Point = namedtuple(..., fields)

        // Point = namedtuple(..., field_names=["x", "y"])

        // Point = namedtuple(..., "x y")

        // Point = namedtuple(..., ("x y"))

        // Point = namedtuple(..., "x, y")

        // Point = namedtuple(..., ["x", "y"])

        PyExpression fields = getFieldsArgumentValue(callExpression, "field_names");

        PyExpression resolvedFields = fields instanceof PyReferenceExpression
                                            ? PyResolveUtil.fullResolveLocally((PyReferenceExpression)fields)
                                            : fields;

        List<String> fieldNames = PyUtil.strListValue(resolvedFields);
        if (fieldNames == null) {
            String resolvedFieldsValue = PyPsiUtils.strValue(resolvedFields);
            if (resolvedFieldsValue == null) {
                return null;
            }
            fieldNames = ContainerUtil.collect(StringUtil.tokenize(resolvedFieldsValue, ", ").iterator());
        }

        PyExpression defaults = getDefaultsArgumentValue(callExpression);
        PyExpression resolvedDefaults = defaults instanceof PyReferenceExpression
                                              ? PyResolveUtil.fullResolveLocally((PyReferenceExpression)defaults)
                                              : defaults;
        int defaultStart = fieldNames.size();
        if (resolvedDefaults instanceof PySequenceExpression seq) {
            defaultStart -= seq.getElements().length;
        }

        LinkedHashMap<String, FieldTypeAndHasDefault> result = new LinkedHashMap<>(fieldNames.size());
        for (int i = 0; i < fieldNames.size(); i++) {
            if (PyNames.PY3_KEYWORDS.contains(fieldNames.get(i)) || fieldNames.get(i).startsWith(PyNames.UNDERSCORE)) {
                fieldNames.set(i, "_" + i);
            }
            result.put(fieldNames.get(i), new FieldTypeAndHasDefault(null, i >= defaultStart));
        }

        return result;
    }

    private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> resolveTypingNTFields(PyCallExpression callExpression) {
        // SUPPORTED CASES:

        // fields = [("x", str), ("y", int)]
        // Point = NamedTuple(..., fields)

        // Point = NamedTuple(..., fields=[("x", str), ("y", int)])

        // Point = NamedTuple(..., [("x", str), ("y", int)])

        // Point = NamedTuple(..., x=str, y=int)

        PyExpression fields = getFieldsArgumentValue(callExpression, "fields");

        if (fields instanceof PyKeywordArgument) {
            PyExpression[] arguments = callExpression.getArguments();
            return getTypingNTFieldsFromKwArguments(Arrays.asList(arguments).subList(1, arguments.length));
        }
        else {
            PyExpression resolvedFields = fields instanceof PyReferenceExpression
                                                ? PyResolveUtil.fullResolveLocally((PyReferenceExpression)fields)
                                                : fields;
            if (!(resolvedFields instanceof PySequenceExpression)) {
                return null;
            }

            return getTypingNTFieldsFromIterable((PySequenceExpression)resolvedFields);
        }
    }

    private static @Nullable PyExpression getFieldsArgumentValue(PyCallExpression callExpression, String possibleKeyword) {
        return flattenParens(callExpression.getArgument(1, possibleKeyword, PyExpression.class));
    }

    private static @Nullable PyExpression getDefaultsArgumentValue(PyCallExpression callExpression) {
        return flattenParens(as(callExpression.getKeywordArgument("defaults"), PyExpression.class));
    }

    private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> getTypingNTFieldsFromKwArguments(List<PyExpression> arguments) {
        LinkedHashMap<String, FieldTypeAndHasDefault> result = new LinkedHashMap<>(arguments.size());

        for (PyExpression argument : arguments) {
            if (!(argument instanceof PyKeywordArgument keywordArgument)) {
                return null;
            }

            String keyword = keywordArgument.getKeyword();
            if (keyword == null) {
                return null;
            }

            result.put(keyword, new FieldTypeAndHasDefault(textIfPresent(keywordArgument.getValueExpression()), false));
        }

        return result;
    }

    private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> getTypingNTFieldsFromIterable(PySequenceExpression fields) {
        LinkedHashMap<String, FieldTypeAndHasDefault> result = new LinkedHashMap<>(fields.getElements().length);

        for (PyExpression element : fields.getElements()) {
            if (!(element instanceof PyParenthesizedExpression)) {
                return null;
            }

            PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
            if (!(contained instanceof PyTupleExpression)) {
                return null;
            }

            PyExpression[] nameAndType = ((PyTupleExpression)contained).getElements();
            if (nameAndType.length != 2) {
                return null;
            }

            String name = tryResolveToText(nameAndType[0]);
            if (name == null) {
                return null;
            }

            result.put(name, new FieldTypeAndHasDefault(textIfPresent(nameAndType[1]), false));
        }

        return result;
    }

    private static @Nullable String textIfPresent(@Nullable PsiElement element) {
        return element == null ? null : element.getText();
    }

    private static @Nullable String tryResolveToText(@Nullable PyExpression expression) {
        if (expression instanceof PyStringLiteralExpression) {
            return ((PyStringLiteralExpression)expression).getStringValue();
        }
        else if (expression instanceof PyReferenceExpression) {
            PyExpression resolved = PyResolveUtil.fullResolveLocally((PyReferenceExpression)expression);
            if (resolved instanceof PyStringLiteralExpression) {
                return ((PyStringLiteralExpression)resolved).getStringValue();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "PyNamedTupleStub(calleeName=" + myCalleeName + ", name=" + myName + ", fields=" + myFields + ')';
    }

    private enum NamedTupleModule {

        COLLECTIONS {
            @Override
            public String getModuleName() {
                return PyNames.COLLECTIONS;
            }
        },

        TYPING {
            @Override
            public String getModuleName() {
                return PyTypingTypeProvider.TYPING;
            }
        };

        public abstract String getModuleName();
    }
}
