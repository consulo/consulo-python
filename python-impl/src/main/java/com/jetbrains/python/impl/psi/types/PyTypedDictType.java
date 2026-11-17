// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.lazy.LazyValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt.isProtocol;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public class PyTypedDictType extends PyClassTypeImpl {
    private final String name;
    private final LazilyEvaluated<Map<String, FieldTypeAndTotality>> lazyFields;
    private final PyClass dictClass;
    private final PyQualifiedNameOwner declaration;
    private final boolean isClosed;
    private final LazilyEvaluated<FieldTypeAndTotality> lazyExtraItems;
    /**
     * Read from the declaration rather than from the item types, which a recursive TypedDict cannot evaluate. An empty list means
     * "not generic"; {@code null} means unstated, which only a TypedDict synthesized from something else is.
     */
    private final @Nullable List<@Nullable PyType> declaredTypeParameters;

    private PyTypedDictType(String name,
                            LazilyEvaluated<Map<String, FieldTypeAndTotality>> lazyFields,
                            PyClass dictClass,
                            boolean isDefinition,
                            PyQualifiedNameOwner declaration,
                            boolean isClosed,
                            LazilyEvaluated<FieldTypeAndTotality> lazyExtraItems,
                            @Nullable List<@Nullable PyType> declaredTypeParameters,
                            List<@Nullable PyType> substitutedTypeArguments) {
        super(dictClass, isDefinition, substitutedTypeArguments);
        this.name = name;
        this.lazyFields = lazyFields;
        this.dictClass = dictClass;
        this.declaration = declaration;
        this.isClosed = isClosed;
        this.lazyExtraItems = lazyExtraItems;
        this.declaredTypeParameters = declaredTypeParameters;
    }

    /**
     * The items and the extra items are evaluated on first access, not on creation: both can refer back to the TypedDict
     * being created, as {@code parent: "Node"} and {@code extra_items="Node"} do.
     */
    public PyTypedDictType(String name,
                           Function<TypeEvalContext, Map<String, FieldTypeAndTotality>> fieldsProvider,
                           PyClass dictClass,
                           boolean isDefinition,
                           PyQualifiedNameOwner declaration,
                           boolean isClosed,
                           Function<TypeEvalContext, FieldTypeAndTotality> extraItemsProvider,
                           @Nullable List<@Nullable PyType> declaredTypeParameters,
                           List<@Nullable PyType> substitutedTypeArguments) {
        this(name,
             new LazilyEvaluated<>(fieldsProvider),
             dictClass, isDefinition, declaration, isClosed,
             new LazilyEvaluated<>(extraItemsProvider),
             declaredTypeParameters,
             substitutedTypeArguments);
    }

    public PyTypedDictType(String name,
                           Function<TypeEvalContext, Map<String, FieldTypeAndTotality>> fieldsProvider,
                           PyClass dictClass,
                           boolean isDefinition,
                           PyQualifiedNameOwner declaration,
                           boolean isClosed,
                           Function<TypeEvalContext, FieldTypeAndTotality> extraItemsProvider,
                           @Nullable List<@Nullable PyType> declaredTypeParameters) {
        this(name, fieldsProvider, dictClass, isDefinition, declaration, isClosed, extraItemsProvider, declaredTypeParameters,
             Collections.emptyList());
    }

    public PyTypedDictType(String name,
                           Function<TypeEvalContext, Map<String, FieldTypeAndTotality>> fieldsProvider,
                           PyClass dictClass,
                           boolean isDefinition,
                           PyQualifiedNameOwner declaration,
                           boolean isClosed,
                           Function<TypeEvalContext, FieldTypeAndTotality> extraItemsProvider) {
        this(name, fieldsProvider, dictClass, isDefinition, declaration, isClosed, extraItemsProvider, null);
    }

    public PyTypedDictType(String name,
                           Function<TypeEvalContext, Map<String, FieldTypeAndTotality>> fieldsProvider,
                           PyClass dictClass,
                           boolean isDefinition,
                           PyQualifiedNameOwner declaration,
                           boolean isClosed) {
        this(name, fieldsProvider, dictClass, isDefinition, declaration, isClosed,
             context -> new FieldTypeAndTotality(null, PyAnyType.getUnknown()));
    }

    public PyTypedDictType(String name,
                           Function<TypeEvalContext, Map<String, FieldTypeAndTotality>> fieldsProvider,
                           PyClass dictClass,
                           boolean isDefinition,
                           PyQualifiedNameOwner declaration) {
        this(name, fieldsProvider, dictClass, isDefinition, declaration, false);
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public @Nullable List<@Nullable PyType> getDeclaredTypeParameters() {
        return declaredTypeParameters;
    }

    public Map<String, FieldTypeAndTotality> fields(TypeEvalContext context) {
        return lazyFields.get(context);
    }

    /**
     * An item without a value expression, {@code extra_items} being a keyword argument rather than an assignment.
     */
    public FieldTypeAndTotality extraItems(TypeEvalContext context) {
        return lazyExtraItems.get(context);
    }

    public @Nullable PyType extraItemsType(TypeEvalContext context) {
        return extraItems(context).getType();
    }

    public TypedDictFieldQualifiers extraItemsQualifiers(TypeEvalContext context) {
        return extraItems(context).getQualifiers();
    }

    /**
     * The arguments as they appear in this TypedDict's notation, {@code null} when the parameters are unstated.
     */
    public @Nullable List<@Nullable PyType> getTypeArgumentsOrDeclaredParameters() {
        List<PyType> typeArguments = getTypeArguments();
        return typeArguments.isEmpty() ? declaredTypeParameters : typeArguments;
    }

    public @Nullable PyType getElementType(String key, TypeEvalContext context) {
        FieldTypeAndTotality field = fields(context).get(key);
        if (field == null) {
            return PyAnyType.getUnknown();
        }
        return field.getType();
    }

    @Override
    public @Nullable PyType getCallType(TypeEvalContext context, PyElement callSite) {
        return isDefinition() ? toInstance() : PyAnyType.getUnknown();
    }

    @Override
    public PyTypedDictType toInstance() {
        return isDefinition()
            ? new PyTypedDictType(name, lazyFields, dictClass, false, declaration, isClosed, lazyExtraItems,
                                  declaredTypeParameters, getTypeArguments())
            : this;
    }

    @Override
    public PyTypedDictType toClass() {
        return isDefinition()
            ? this
            : new PyTypedDictType(name, lazyFields, dictClass, true, declaration, isClosed, lazyExtraItems,
                                  declaredTypeParameters, getTypeArguments());
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public boolean isCallable() {
        return isDefinition();
    }

    @Override
    public @Nullable List<PyCallableParameter> getParameters(TypeEvalContext context) {
        if (isCallable()) {
            PyType extraItemsType = extraItemsType(context);
            if (fields(context).isEmpty() && isUnknown(extraItemsType)) {
                return Collections.emptyList();
            }
            else {
                PyCallableParameter singleStarParameter = PyCallableParameterImpl.keywordOnlySeparatorNonPsi();

                List<PyCallableParameter> fieldParameters = new ArrayList<>();
                for (Map.Entry<String, FieldTypeAndTotality> entry : fields(context).entrySet()) {
                    String key = entry.getKey();
                    FieldTypeAndTotality value = entry.getValue();
                    if (Boolean.TRUE.equals(value.getQualifiers().isRequired())) {
                        fieldParameters.add(PyCallableParameterImpl.nonPsi(key, value.getType()));
                    }
                    else {
                        fieldParameters.add(PyCallableParameterImpl.nonPsi(key, value.getType(), PyNames.ELLIPSIS));
                    }
                }

                List<PyCallableParameter> extraItemsParam;
                if (!isUnknown(extraItemsType) && !isClosed) {
                    extraItemsParam = List.of(PyCallableParameterImpl.keywordContainerNonPsi("kwargs", extraItemsType));
                }
                else {
                    extraItemsParam = Collections.emptyList();
                }

                List<PyCallableParameter> result = new ArrayList<>();
                result.add(singleStarParameter);
                result.addAll(fieldParameters);
                result.addAll(extraItemsParam);
                return result;
            }
        }
        else {
            return null;
        }
    }

    @Override
    public @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        List<PyCallableParameter> parameters = getParameters(context);
        return parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null;
    }

    @Override
    public String toString() {
        return "PyTypedDictType: " + name;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        PyTypedDictType otherType = (PyTypedDictType)other;
        // Every TypedDict is built on `dict`, so only the declaration tells two of them apart.
        return declaration.equals(otherType.declaration) && super.equals(other);
    }

    @Override
    public int hashCode() {
        return cachedHashCode.get();
    }

    private final Supplier<Integer> cachedHashCode = LazyValue.atomicNotNull(this::computeHashCode);

    private int computeHashCode() {
        return Objects.hash(super.hashCode(), declaration);
    }

    @Override
    public PyQualifiedNameOwner getDeclarationElement() {
        return declaration;
    }

    /**
     * isRequired is true - if value type is Required, false - if it is NotRequired, and null if it does not have any type specification
     */
    public static final class TypedDictFieldQualifiers {
        private final @Nullable Boolean isRequired;
        private final boolean isReadOnly;
        private final boolean hasExplicitRequiredQualifier;

        public TypedDictFieldQualifiers(@Nullable Boolean isRequired, boolean isReadOnly, boolean hasExplicitRequiredQualifier) {
            this.isRequired = isRequired;
            this.isReadOnly = isReadOnly;
            this.hasExplicitRequiredQualifier = hasExplicitRequiredQualifier;
        }

        public TypedDictFieldQualifiers(@Nullable Boolean isRequired, boolean isReadOnly) {
            this(isRequired, isReadOnly, false);
        }

        public TypedDictFieldQualifiers(@Nullable Boolean isRequired) {
            this(isRequired, false);
        }

        public TypedDictFieldQualifiers() {
            this(true);
        }

        public @Nullable Boolean isRequired() {
            return isRequired;
        }

        public boolean isReadOnly() {
            return isReadOnly;
        }

        public boolean getHasExplicitRequiredQualifier() {
            return hasExplicitRequiredQualifier;
        }

        public TypedDictFieldQualifiers copy(@Nullable Boolean isRequired, boolean isReadOnly, boolean hasExplicitRequiredQualifier) {
            return new TypedDictFieldQualifiers(isRequired, isReadOnly, hasExplicitRequiredQualifier);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TypedDictFieldQualifiers that)) {
                return false;
            }
            return Objects.equals(isRequired, that.isRequired) &&
                isReadOnly == that.isReadOnly &&
                hasExplicitRequiredQualifier == that.hasExplicitRequiredQualifier;
        }

        @Override
        public int hashCode() {
            return Objects.hash(isRequired, isReadOnly, hasExplicitRequiredQualifier);
        }

        @Override
        public String toString() {
            return "TypedDictFieldQualifiers(isRequired=" + isRequired + ", isReadOnly=" + isReadOnly +
                ", hasExplicitRequiredQualifier=" + hasExplicitRequiredQualifier + ")";
        }
    }

    public static final class FieldTypeAndTotality {
        private final @Nullable PyExpression value;
        private final @Nullable PyType type;
        private final TypedDictFieldQualifiers qualifiers;

        public FieldTypeAndTotality(@Nullable PyExpression value, @Nullable PyType type, TypedDictFieldQualifiers qualifiers) {
            this.value = value;
            this.type = type;
            this.qualifiers = qualifiers;
            PyAnyType.validate(type);
        }

        public FieldTypeAndTotality(@Nullable PyExpression value, @Nullable PyType type) {
            this(value, type, new TypedDictFieldQualifiers());
        }

        public @Nullable PyExpression getValue() {
            return value;
        }

        public @Nullable PyType getType() {
            return type;
        }

        public TypedDictFieldQualifiers getQualifiers() {
            return qualifiers;
        }

        public boolean isRequired() {
            Boolean isRequired = qualifiers.isRequired();
            return isRequired != null ? isRequired : true;
        }

        public boolean isReadOnly() {
            return qualifiers.isReadOnly();
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FieldTypeAndTotality that)) {
                return false;
            }
            return Objects.equals(value, that.value) && Objects.equals(type, that.type) && qualifiers.equals(that.qualifiers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, type, qualifiers);
        }

        @Override
        public String toString() {
            return "FieldTypeAndTotality(value=" + value + ", type=" + type + ", qualifiers=" + qualifiers + ")";
        }
    }

    /**
     * A single failed TypedDict key, reported by {@link #match} while a breakdown is being collected. Carries the raw
     * value types ({@code expectedValue}/{@code actualValue}) so the caller ({@link PyTypeChecker}) can codify them against its
     * anchor into a {@code PyMismatchStep.TypedDictKey}; {@code actualValue} is null for a {@link TypedDictKeyProblem#MISSING} key.
     */
    public record TypedDictKeyMismatch(String key, TypedDictKeyProblem problem, @Nullable PyType expectedValue,
                                       @Nullable PyType actualValue) {
        public String getKey() {
            return key;
        }

        public TypedDictKeyProblem getProblem() {
            return problem;
        }

        public @Nullable PyType getExpectedValue() {
            return expectedValue;
        }

        public @Nullable PyType getActualValue() {
            return actualValue;
        }
    }

    public static final String TYPED_DICT_TOTAL_PARAMETER = "total";
    public static final String TYPED_DICT_EXTRA_ITEMS_PARAMETER = "extra_items";
    public static final String TYPED_DICT_CLOSED_PARAMETER = "closed";

    /**
     * {@code expression} matches {@code expectedType} if:
     * <ul>
     * <li>all required keys from {@code expectedType} are present in {@code expression}</li>
     * <li>all keys from {@code expression} are present in {@code expectedType}</li>
     * <li>each key has the same value type in {@code expectedType} and {@code expression}</li>
     * </ul>
     */
    public static void checkExpression(PyTypedDictType expectedType,
                                       PyExpression expression,
                                       TypeEvalContext context,
                                       TypeCheckingResult result) {
        assert isDictExpression(expression, context);
        Map<String, Pair<@Nullable PyExpression, @Nullable PyType>> actualFields = getTypedDictFieldsFromExpression(expression, context);
        if (actualFields == null) {
            result.getValueTypeErrors().add(new ValueTypeError(expression, expectedType, context.getType(expression)));
            return;
        }

        PyType extraItemsType = expectedType.extraItemsType(context);
        boolean isClosed = expectedType.isClosed();

        actualFields.forEach((key, pair) -> {
            PyExpression actualFieldValue = pair.getFirst();
            PyType actualFieldType = pair.getSecond();
            if (expectedType.fields(context).containsKey(key)) {
                FieldTypeAndTotality expectedField = expectedType.fields(context).get(key);
                PyType expectedFieldType = expectedField != null ? expectedField.getType() : null;
                if (expectedFieldType instanceof PyTypedDictType expectedTypedDict &&
                    actualFieldValue != null &&
                    isDictExpression(actualFieldValue, context)) {
                    checkExpression(expectedTypedDict, actualFieldValue, context, result);
                }
                else {
                    PyType promotedType;
                    if (actualFieldValue != null) {
                        PyType promoted = PyLiteralType.promoteToLiteral(actualFieldValue, expectedFieldType, context, null);
                        promotedType = !isUnknown(promoted) ? promoted : context.getType(actualFieldValue);
                    }
                    else {
                        promotedType = actualFieldType;
                    }
                    // `promotedType` may preserve element literals inside an invariant container (e.g. `list[Literal['b']]`
                    // for `["b"]`), which spuriously fails the invariant match against the field's own widened type
                    // (`list[str]`). If the value's natural (un-promoted) type already matches, the fresh literal is
                    // assignable and there is no real mismatch. Mirrors the assignment-statement handling in
                    // PyTypeCheckerInspection.
                    // temporary special casing to avoid Literal problems PY-90366
                    if (!match(expectedFieldType, promotedType, actualFieldValue, context, result) &&
                        (Objects.equals(actualFieldType, promotedType) ||
                            !match(expectedFieldType, actualFieldType, actualFieldValue, context, result))) {
                        result.getValueTypeErrors().add(new ValueTypeError(actualFieldValue, expectedFieldType, actualFieldType));
                    }
                }
            }
            else if (!isUnknown(extraItemsType) && !isClosed) {
                if (!match(extraItemsType, actualFieldType, actualFieldValue, context, result)) {
                    result.getValueTypeErrors().add(new ValueTypeError(actualFieldValue, extraItemsType, actualFieldType));
                }
            }
            else {
                PyExpression extraKeyToHighlight = PsiTreeUtil.getParentOfType(actualFieldValue, PyKeyValueExpression.class);
                if (extraKeyToHighlight == null) {
                    extraKeyToHighlight = PsiTreeUtil.getParentOfType(actualFieldValue, PyKeywordArgument.class);
                }
                if (extraKeyToHighlight == null) {
                    extraKeyToHighlight = actualFieldValue;
                }
                result.getExtraKeys().add(new ExtraKeyError(extraKeyToHighlight, expectedType.getName(), key));
            }
        });

        List<String> missingKeys = new ArrayList<>();
        for (Map.Entry<String, FieldTypeAndTotality> entry : expectedType.fields(context).entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue().getQualifiers().isRequired()) && !actualFields.containsKey(entry.getKey())) {
                missingKeys.add(entry.getKey());
            }
        }
        if (!missingKeys.isEmpty()) {
            result.getMissingKeys().add(new MissingKeysError(expression, expectedType.getName(), missingKeys));
        }
    }

    private static boolean match(@Nullable PyType expectedType,
                                 @Nullable PyType actualType,
                                 @Nullable PyExpression actualExpression,
                                 TypeEvalContext context,
                                 TypeCheckingResult result) {
        if (actualExpression != null && isDictExpression(actualExpression, context)) {
            List<@Nullable PyType> types = PyTypeUtil.toStream(expectedType).toList();
            for (PyType subType : types) {
                if (subType instanceof PyTypedDictType typedDictSubType) {
                    TypeCheckingResult res = types.size() <= 1 ? result : new TypeCheckingResult();
                    checkExpression(typedDictSubType, actualExpression, context, res);
                    if (!res.getHasErrors()) {
                        return true;
                    }
                }
                else {
                    if (strictUnionMatch(subType, actualType, context)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return strictUnionMatch(expectedType, actualType, context);
    }

    private static boolean strictUnionMatch(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context) {
        if (!PyUnionType.isStrictSemanticsEnabled()) {
            return PyTypeUtil.toStream(actual).allMatch(type -> PyTypeChecker.match(expected, type, context));
        }
        return PyTypeChecker.match(expected, actual, context);
    }

    public static @Nullable Boolean match(PyType expected, PyTypedDictType actual, TypeEvalContext context) {
        return match(expected, actual, context, null);
    }

    /**
     * Rules for type-checking TypedDicts are described in PEP-589
     *
     * @see <a href=https://www.python.org/dev/peps/pep-0589/#type-consistency>PEP-589</a>
     */
    public static @Nullable Boolean match(PyType expected,
                                          PyTypedDictType actual,
                                          TypeEvalContext context,
                                          @Nullable Consumer<TypedDictKeyMismatch> mismatch) {
        if (expected instanceof PyClassType expectedClassType && !(expected instanceof PyTypedDictType) && expectedClassType.isParameterized()) {
            Boolean collectionMatch = matchTypedDictWithCollection(expectedClassType, actual, context);
            if (collectionMatch != null) {
                return collectionMatch;
            }
        }

        if (expected instanceof PyClassLikeType expectedClassLikeType && isProtocol(expectedClassLikeType, context)) {
            return null;
        }

        if (!(expected instanceof PyTypedDictType expectedTypedDict)) {
            return false;
        }

        for (Map.Entry<String, FieldTypeAndTotality> entry : expectedTypedDict.fields(context).entrySet()) {
            String expectedKey = entry.getKey();
            FieldTypeAndTotality expectedField = entry.getValue();
            if (expectedField.isReadOnly() &&
                !expectedField.isRequired() &&
                expectedField.getType() != null &&
                PyNames.OBJECT.equals(expectedField.getType().getName())) {
                continue;
            }
            FieldTypeAndTotality actualField = actual.fields(context).get(expectedKey);
            if (actualField == null) {
                if (mismatch != null) {
                    mismatch.accept(new TypedDictKeyMismatch(expectedKey, TypedDictKeyProblem.MISSING, expectedField.getType(), null));
                }
                return false;
            }
            if (!strictUnionMatch(expectedField.getType(), actualField.getType(), context)) {
                if (mismatch != null) {
                    mismatch.accept(new TypedDictKeyMismatch(expectedKey, TypedDictKeyProblem.VALUE_TYPE, expectedField.getType(),
                                                             actualField.getType()));
                }
                return false;
            }
            if (!expectedField.isReadOnly()) {
                if (!(strictUnionMatch(actualField.getType(), expectedField.getType(), context) && !actualField.isReadOnly())) {
                    // A mutable key is invariant: the value types must match both ways and the source key must stay writable.
                    TypedDictKeyProblem problem = actualField.isReadOnly() ? TypedDictKeyProblem.READONLY : TypedDictKeyProblem.VALUE_TYPE;
                    if (mismatch != null) {
                        mismatch.accept(new TypedDictKeyMismatch(expectedKey, problem, expectedField.getType(), actualField.getType()));
                    }
                    return false;
                }
            }
            if (expectedField.isRequired()) {
                if (!actualField.isRequired()) {
                    if (mismatch != null) {
                        mismatch.accept(new TypedDictKeyMismatch(expectedKey, TypedDictKeyProblem.REQUIRED, expectedField.getType(),
                                                                 actualField.getType()));
                    }
                    return false;
                }
            }
            else {
                if (!expectedField.isReadOnly()) {
                    if (actualField.isRequired()) {
                        if (mismatch != null) {
                            mismatch.accept(new TypedDictKeyMismatch(expectedKey, TypedDictKeyProblem.REQUIRED, expectedField.getType(),
                                                                     actualField.getType()));
                        }
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean isDictExpression(PyExpression expression, TypeEvalContext context) {
        if (expression instanceof PyDictLiteralExpression) {
            return true;
        }
        if (expression instanceof PyCallExpression callExpression) {
            PyExpression callee = callExpression.getCallee();
            if (callee != null) {
                return PyTypingTypeProvider.resolveToQualifiedNames(callee, context).stream().anyMatch(it -> PyNames.FQN.DICT.equals(it));
            }
        }
        return false;
    }

    private static @Nullable Map<String, Pair<@Nullable PyExpression, @Nullable PyType>> getTypedDictFieldsFromExpression(
        PyExpression expression,
        TypeEvalContext context
    ) {
        assert isDictExpression(expression, context);
        if (expression instanceof PyDictLiteralExpression dictLiteral) {
            return PyCollectionTypeUtil.getTypedDictFieldsFromDictLiteral(dictLiteral, context);
        }
        else {
            return getTypedDictFieldsFromDictConstructorCall((PyCallExpression)expression, context);
        }
    }

    private static @Nullable Map<String, Pair<@Nullable PyExpression, @Nullable PyType>> getTypedDictFieldsFromDictConstructorCall(
        PyCallExpression callExpression,
        TypeEvalContext context
    ) {
        PyExpression callee = callExpression.getCallee();
        if (callee == null) {
            return null;
        }
        if (PyTypingTypeProvider.resolveToQualifiedNames(callee, context).stream().anyMatch(it -> PyNames.FQN.DICT.equals(it))) {
            PyExpression[] arguments = callExpression.getArguments();
            if (arguments.length > 1) {
                Map<String, Pair<@Nullable PyExpression, @Nullable PyType>> fields = new LinkedHashMap<>();
                for (PyExpression argument : arguments) {
                    if (!(argument instanceof PyKeywordArgument keywordArgument)) {
                        return null;
                    }
                    String keyword = keywordArgument.getKeyword();
                    if (keyword == null) {
                        return null;
                    }
                    PyExpression valueExpression = keywordArgument.getValueExpression();
                    fields.put(keyword, Pair.create(valueExpression, valueExpression != null ? context.getType(valueExpression) : null));
                }
                return fields;
            }
        }
        return null;
    }

    private static @Nullable Boolean matchTypedDictWithCollection(PyClassType expected, PyTypedDictType actual, TypeEvalContext context) {
        String expectedClassQName = expected.getClassQName();
        boolean isMapping = PyTypingTypeProvider.MAPPING.equals(expectedClassQName);
        if (!isMapping && !PyNames.FQN.DICT.equals(expectedClassQName)) {
            return null;
        }

        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(actual.dictClass);
        List<PyType> elementTypes = expected.getTypeArguments();

        if (elementTypes.size() != 2 || !Objects.equals(builtinCache.getStrType(), elementTypes.get(0))) {
            return false;
        }

        PyType expectedValueType = elementTypes.get(1);
        PyType extraItemsType = actual.extraItemsType(context);
        // Extra items are present only when they are explicitly typed and the TypedDict is not closed
        // (closed=True is equivalent to extra_items=Never).
        boolean hasExtraItems = extraItemsType != null && !extraItemsType.equals(PyNeverType.NEVER) && !actual.isClosed();

        if (isMapping) {
            // A TypedDict is assignable to Mapping[str, VT] when every value type of its items is assignable to VT.
            // An open (non-closed) TypedDict is considered to have read-only extra items of type 'object'.
            List<@Nullable PyType> valueTypes = new ArrayList<>();
            for (FieldTypeAndTotality field : actual.fields(context).values()) {
                if (field.getType() != null) {
                    valueTypes.add(field.getType());
                }
            }
            if (actual.isClosed() || Objects.equals(extraItemsType, PyNeverType.NEVER)) {
            }
            else if (!isUnknown(extraItemsType)) {
                valueTypes.add(extraItemsType);
            }
            else {
                PyClassType objectType = builtinCache.getObjectType();
                if (objectType != null) {
                    valueTypes.add(objectType);
                }
            }
            return PyTypeChecker.match(expectedValueType, PyUnionType.unionOrUnknown(valueTypes), context);
        }
        else {
            // A TypedDict is generally not assignable to `dict[str, X]` because `dict` is mutable and
            // invariant, so its known keys could be deleted or have their value types broken. However,
            // `dict[str, Any]` uses `Any` as the value type, which opts out of value-type checking
            // (the common "JSON-like" usage), so accept any TypedDict here. See PY-85704.
            if (isAnyOrUnknown(expectedValueType)) {
                return actual.fields(context).values().stream().noneMatch(FieldTypeAndTotality::isReadOnly);
            }
            // A TypedDict is assignable to dict[str, VT] only when it has mutable extra items equivalent to VT
            // and every declared item is mutable, non-required, and has a value type equivalent to VT.
            return hasExtraItems &&
                !actual.extraItemsQualifiers(context).isReadOnly() &&
                areEquivalent(extraItemsType, expectedValueType, context) &&
                actual.fields(context).values().stream().allMatch(field ->
                                                                      !field.isReadOnly() &&
                                                                          !Boolean.TRUE.equals(field.getQualifiers().isRequired()) &&
                                                                          areEquivalent(field.getType(), expectedValueType, context)
                );
        }
    }

    private static boolean areEquivalent(@Nullable PyType left, @Nullable PyType right, TypeEvalContext context) {
        return PyTypeChecker.match(right, left, context) && PyTypeChecker.match(left, right, context);
    }

    public record MissingKeysError(@Nullable PyExpression actualExpression, String expectedTypedDictName, List<String> missingKeys) {
        public @Nullable PyExpression getActualExpression() {
            return actualExpression;
        }

        public String getExpectedTypedDictName() {
            return expectedTypedDictName;
        }

        public List<String> getMissingKeys() {
            return missingKeys;
        }
    }

    public record ExtraKeyError(@Nullable PyExpression actualExpression, String expectedTypedDictName, String key) {
        public @Nullable PyExpression getActualExpression() {
            return actualExpression;
        }

        public String getExpectedTypedDictName() {
            return expectedTypedDictName;
        }

        public String getKey() {
            return key;
        }
    }

    public record ValueTypeError(@Nullable PyExpression actualExpression, @Nullable PyType expectedType, @Nullable PyType actualType) {
        public @Nullable PyExpression getActualExpression() {
            return actualExpression;
        }

        public @Nullable PyType getExpectedType() {
            return expectedType;
        }

        public @Nullable PyType getActualType() {
            return actualType;
        }
    }

    public static class TypeCheckingResult {
        private final List<ValueTypeError> valueTypeErrors = new ArrayList<>();
        private final List<MissingKeysError> missingKeys = new ArrayList<>();
        private final List<ExtraKeyError> extraKeys = new ArrayList<>();

        public List<ValueTypeError> getValueTypeErrors() {
            return valueTypeErrors;
        }

        public List<MissingKeysError> getMissingKeys() {
            return missingKeys;
        }

        public List<ExtraKeyError> getExtraKeys() {
            return extraKeys;
        }

        public boolean getHasErrors() {
            return !valueTypeErrors.isEmpty() || !missingKeys.isEmpty() || !extraKeys.isEmpty();
        }
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyTypedDictType(this);
        }
        return visitor.visitPyClassType(this);
    }

    private static final class LazilyEvaluated<T> {
        private volatile @Nullable Function<TypeEvalContext, T> provider;
        private final AtomicReference<@Nullable T> value = new AtomicReference<>();

        LazilyEvaluated(Function<TypeEvalContext, T> provider) {
            this.provider = provider;
        }

        T get(TypeEvalContext context) {
            T existing = value.get();
            if (existing != null) {
                return existing;
            }
            Function<TypeEvalContext, T> pending = provider;
            if (pending == null) {
                return Objects.requireNonNull(value.get());
            }
            T computed = pending.apply(context);
            if (!value.compareAndSet(null, computed)) {
                return Objects.requireNonNull(value.get());
            }
            provider = null;
            return computed;
        }
    }
}
