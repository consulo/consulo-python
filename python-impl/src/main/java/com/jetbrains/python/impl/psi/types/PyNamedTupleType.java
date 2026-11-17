// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.ImmutableSet;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.util.lang.function.Condition;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.impl.psi.resolve.CompletionVariantsProcessor;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.platform.base.icon.PlatformIconGroup;


public class PyNamedTupleType extends PyTupleType implements PyCallableType {
    private static final ImmutableSet<String> PYTHON_2_ATTRIBUTES =
        ImmutableSet.of("_make", "_asdict", "_replace", "_fields", "_field_defaults", "_field_types");
    private static final ImmutableSet<String> PYTHON_3_ATTRIBUTES =
        ImmutableSet.of("_make", "_asdict", "_replace", "_fields", "_field_defaults");

    public static Set<String> getSpecialAttributes(LanguageLevel level) {
        if (level.isPy3K()) {
            return PYTHON_3_ATTRIBUTES;
        }
        else {
            return PYTHON_2_ATTRIBUTES;
        }
    }

    private final String myName;

    private final LinkedHashMap<String, FieldTypeAndDefaultValue> myFields;

    private final boolean myTyped;

    private final @Nullable PyQualifiedNameOwner myDeclaration;

    public PyNamedTupleType(PyClass tupleClass,
                            String name,
                            LinkedHashMap<String, FieldTypeAndDefaultValue> fields,
                            boolean isDefinition,
                            boolean typed,
                            @Nullable PyQualifiedNameOwner declaration) {
        this(tupleClass, name, fields, isDefinition, typed, declaration, Collections.emptyList());
    }

    /**
     * @param typeArguments the type arguments of a generic named tuple class, such as {@code [str]} for {@code Base[str]}.
     *                      The field types must already have these type arguments applied.
     */
    public PyNamedTupleType(PyClass tupleClass,
                            String name,
                            LinkedHashMap<String, FieldTypeAndDefaultValue> fields,
                            boolean isDefinition,
                            boolean typed,
                            @Nullable PyQualifiedNameOwner declaration,
                            List<? extends PyType> typeArguments) {
        super(tupleClass,
              ContainerUtil.map(fields.values(), typeAndValue -> typeAndValue.getType()),
              false,
              isDefinition,
              typeArguments);

        myFields = new LinkedHashMap<>(fields);
        myName = name;
        myTyped = typed;
        myDeclaration = declaration;
    }

    /**
     * The type arguments of the named tuple class, as in {@code Base[str]}. They are empty for a named tuple that is not generic,
     * or not parameterized. Use {@link #getElementTypes()} or {@link #getFields()} for the field types.
     */
    @Override
    public List<PyType> getTypeArguments() {
        return Collections.unmodifiableList(myTypeArguments);
    }

    /**
     * Returns a copy of this named tuple. The copy applies {@code fieldTypeMapper} to each field type,
     * and {@code typeArgumentsMapper} to the list of type arguments.
     */
    public PyNamedTupleType mapTypes(Function<? super PyType, ? extends PyType> fieldTypeMapper,
                                              Function<? super List<PyType>, ? extends List<? extends PyType>> typeArgumentsMapper) {
        LinkedHashMap<String, FieldTypeAndDefaultValue> newFields = new LinkedHashMap<>();
        for (Map.Entry<String, FieldTypeAndDefaultValue> entry : myFields.entrySet()) {
            FieldTypeAndDefaultValue field = entry.getValue();
            newFields.put(entry.getKey(), new FieldTypeAndDefaultValue(fieldTypeMapper.apply(field.getType()), field.getDefaultValue()));
        }
        return withUserDataCopy(new PyNamedTupleType(myClass, myName, newFields, isDefinition(), myTyped, myDeclaration,
                                                     typeArgumentsMapper.apply(getTypeArguments())));
    }

    @Override
    public PyQualifiedNameOwner getDeclarationElement() {
        return ObjectUtil.notNull(myDeclaration, super::getDeclarationElement);
    }

    @Override
    public Object [] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        List<Object> result = new ArrayList<>();
        Collections.addAll(result, super.getCompletionVariants(completionPrefix, location, context));

        for (String field : myFields.keySet()) {
            result.add(
                LookupElementBuilder.create(field).withIcon(PlatformIconGroup.nodesField()));
        }

        if (completionPrefix == null) {
            Condition<String> nameFilter = getSpecialAttributes(LanguageLevel.forElement(location))::contains;
            CompletionVariantsProcessor processor =
                new CompletionVariantsProcessor(location, null, nameFilter, false, context.get(CTX_SUPPRESS_PARENTHESES) != null);

            myClass.processClassLevelDeclarations(processor);

            result.addAll(processor.getResultList());
        }

        return ArrayUtil.toObjectArray(result);
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public @Nullable PyNamedTupleType getCallType(TypeEvalContext context, PyElement callSite) {
        return isDefinition() ? toInstance() : null;
    }

    @Override
    public @Nullable PyNamedTupleType getCallType(TypeEvalContext context,
                                                  @Nullable PyElement callSite,
                                                  List<PyCallableArgument> arguments) {
        if (isDefinition()) {
            return getCallDefinitionType(arguments, context);
        }

        return null;
    }

    @Override
    public PyNamedTupleType toInstance() {
        return isDefinition()
               ? new PyNamedTupleType(myClass, myName, myFields, false, myTyped, myDeclaration, myTypeArguments)
               : this;
    }

    @Override
    public PyNamedTupleType toClass() {
        return !isDefinition()
               ? new PyNamedTupleType(myClass, myName, myFields, true, myTyped, myDeclaration, myTypeArguments)
               : this;
    }

    @Override
    public String toString() {
        return "PyNamedTupleType: " + myName + (myTypeArguments.isEmpty() ? "" : "[" + StringUtil.join(myTypeArguments, String::valueOf, ", ") + "]");
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        PyNamedTupleType type = (PyNamedTupleType)o;
        return Objects.equals(myName, type.myName) &&
               Objects.equals(myFields.keySet(), type.myFields.keySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), myName, myFields.keySet());
    }

    @Override
    public Set<String> getMemberNames(boolean inherited, TypeEvalContext context) {
        Set<String> result = super.getMemberNames(inherited, context);
        result.addAll(myFields.keySet());

        return result;
    }

    public Map<String, FieldTypeAndDefaultValue> getFields() {
        return Collections.unmodifiableMap(myFields);
    }

    @Override
    public boolean isCallable() {
        return isDefinition();
    }

    @Override
    public @Nullable List<PyCallableParameter> getParameters(TypeEvalContext context) {
        return isCallable()
               ? ContainerUtil.map(myFields.entrySet(), field -> fieldToCallableParameter(field.getKey(), field.getValue()))
               : null;
    }

    @Override
    public @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        List<PyCallableParameter> parameters = getParameters(context);
        return parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null;
    }

    public boolean isTyped() {
        return myTyped;
    }

    public PyNamedTupleType clarifyFields(Map<String, PyType> fieldNameToType) {
        if (!myTyped) {
            LinkedHashMap<String, FieldTypeAndDefaultValue> newFields = new LinkedHashMap<>(myFields);

            for (Map.Entry<String, PyType> entry : fieldNameToType.entrySet()) {
                String fieldName = entry.getKey();

                if (newFields.containsKey(fieldName)) {
                    newFields.put(fieldName, new FieldTypeAndDefaultValue(entry.getValue(), null));
                }
            }

            return new PyNamedTupleType(myClass, myName, newFields, isDefinition(), false, myDeclaration, myTypeArguments);
        }

        return this;
    }

    private PyNamedTupleType getCallDefinitionType(List<PyCallableArgument> arguments,
                                                            TypeEvalContext context) {
        if (!myTyped) {
            if (arguments.size() == myFields.size()) {
                Map<String, PyType> result = new HashMap<>();

                for (Map.Entry<String, PyCallableArgument> entry : StreamEx.ofKeys(myFields).zipWith(StreamEx.of(arguments))) {
                    String name = entry.getKey();
                    PyType type = entry.getValue().getType(context);

                    result.put(name, type);
                }

                return toInstance().clarifyFields(result);
            }
        }

        return toInstance();
    }

    private static PyCallableParameter fieldToCallableParameter(String name,
                                                                         FieldTypeAndDefaultValue typeAndDefaultValue) {
        return PyCallableParameterImpl.nonPsi(name, typeAndDefaultValue.getType(), typeAndDefaultValue.getDefaultValue());
    }

    public static class FieldTypeAndDefaultValue {

        private final @Nullable PyType myType;

        private final @Nullable PyExpression myDefaultValue;

        public FieldTypeAndDefaultValue(@Nullable PyType type, @Nullable PyExpression defaultValue) {
            PyAnyType.validate(type);
            myType = type;
            myDefaultValue = defaultValue;
        }

        public @Nullable PyType getType() {
            return myType;
        }

        public @Nullable PyExpression getDefaultValue() {
            return myDefaultValue;
        }
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyNamedTupleType(this);
        }
        return visitor.visitPyClassType(this);
    }
}
