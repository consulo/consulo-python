// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.impl.PyPsiFacadeImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyLiteralKind;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import consulo.application.util.registry.Registry;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Represents literal type introduced in PEP 586.
 */
public class PyLiteralType extends PyClassTypeImpl {
    private sealed interface PyLiteralValue {
        String getText();

        record IntValue(BigInteger value) implements PyLiteralValue {
            @Override
            public String getText() {
                return value.toString();
            }
        }

        final class StringValue implements PyLiteralValue {
            private final String stringValue;
            private final String text;

            StringValue(String stringValue, String text) {
                this.stringValue = stringValue;
                this.text = text;
            }

            String getStringValue() {
                return stringValue;
            }

            @Override
            public String getText() {
                return text;
            }

            // Different string literals may have the same stringValue: 'str' vs "str"
            @Override
            public boolean equals(@Nullable Object other) {
                return this == other || other instanceof StringValue otherValue && stringValue.equals(otherValue.stringValue);
            }

            @Override
            public int hashCode() {
                return stringValue.hashCode();
            }
        }

        record BoolValue(boolean value) implements PyLiteralValue {
            @Override
            public String getText() {
                return value ? PyNames.TRUE : PyNames.FALSE;
            }
        }

        record EnumMemberValue(PyClass enumClass, String memberName) implements PyLiteralValue {
            @Override
            public String getText() {
                return enumClass.getName() + "." + memberName;
            }
        }
    }

    private final PyLiteralValue value;

    private PyLiteralType(PyClass cls, PyLiteralValue value) {
        this(cls, value, false);
    }

    private PyLiteralType(PyClass cls, PyLiteralValue value, boolean isDefinition) {
        super(cls, isDefinition);
        this.value = value;
    }

    @Override
    public String getName() {
        return "Literal[" + getExpressionText() + "]";
    }

    public String getExpressionText() {
        return value.getText();
    }

    /**
     * Returns the decoded string value if this literal type represents a string, {@code null} otherwise.
     */
    public @Nullable String getStringValue() {
        return value instanceof PyLiteralValue.StringValue stringValue ? stringValue.getStringValue() : null;
    }

    /**
     * Returns the integer value if this literal type represents an integer, {@code null} otherwise.
     */
    public @Nullable BigInteger getIntValue() {
        return value instanceof PyLiteralValue.IntValue intValue ? intValue.value() : null;
    }

    /**
     * Returns the boolean value if this literal type represents a boolean, {@code null} otherwise.
     */
    public @Nullable Boolean getBoolValue() {
        return value instanceof PyLiteralValue.BoolValue boolValue ? boolValue.value() : null;
    }

    /**
     * Returns the enum member name if this literal type represents an enum member, {@code null} otherwise.
     * The enum class itself is available via {@link #getPyClass()}.
     */
    public @Nullable String getEnumMemberName() {
        return value instanceof PyLiteralValue.EnumMemberValue enumMemberValue ? enumMemberValue.memberName() : null;
    }

    @Override
    public String toString() {
        return "PyLiteralType: " + getExpressionText();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other ||
            other != null && getClass() == other.getClass() && getPyClass().equals(((PyLiteralType)other).getPyClass()) &&
                value.equals(((PyLiteralType)other).value);
    }

    @Override
    public int hashCode() {
        return 31 * getPyClass().hashCode() + value.hashCode();
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyLiteralType(this);
        }
        return visitor.visitPyClassType(this);
    }

    @Override
    public PyClassType toClass() {
        return myIsDefinition ? this : new PyLiteralType(getPyClass(), value, true);
    }

    @Override
    public PyClassType toInstance() {
        return !myIsDefinition ? this : new PyLiteralType(getPyClass(), value, false);
    }

    /**
     * Whether literal expressions (integer, boolean and string literals) infer their own literal type
     * (e.g. {@code Literal[42]} for {@code 42}) instead of the corresponding wide type ({@code int}).
     * <p>
     * Controlled by the {@code python.typing.literal.types.for.literals} registry flag, enabled by default.
     */
    public static boolean inferLiteralTypeForLiteralExpressions() {
        return Registry.is("python.typing.literal.types.for.literals", true);
    }

    public static @Nullable PyType fromLiteralParameter(PyExpression expression, TypeEvalContext context) {
        return fromLiteralParameter(expression, context, false);
    }

    /**
     * Tries to construct literal type for index passed to {@code typing.Literal[...]}
     */
    public static @Nullable PyType fromLiteralParameter(PyExpression expression, TypeEvalContext context, boolean typeRepresentation) {
        if (expression instanceof PyTupleExpression tupleExpression) {
            PyExpression[] elements = tupleExpression.getElements();
            List<@Nullable PyType> classes = new ArrayList<>();
            for (PyExpression element : elements) {
                PyType type = createFromLiteralParameter(element, context, typeRepresentation);
                if (type != null) {
                    classes.add(type);
                }
            }
            return elements.length == classes.size() ? PyUnionType.unionOrUnknown(classes) : null;
        }
        return createFromLiteralParameter(expression, context, typeRepresentation);
    }

    public static PyLiteralType enumMember(PyClass enumClass, String memberName) {
        return new PyLiteralType(enumClass, new PyLiteralValue.EnumMemberValue(enumClass, memberName));
    }

    public static @Nullable PyLiteralType stringLiteral(PsiElement anchor, String value) {
        PyClassType strType = PyBuiltinCache.getInstance(anchor).getStrType();
        PyClass strClass = strType != null ? strType.getPyClass() : null;
        return strClass != null ? new PyLiteralType(strClass, new PyLiteralValue.StringValue(value, "\"" + value + "\"")) : null;
    }

    /**
     * Build a {@code Literal[<integer>]} type from a raw {@link BigInteger} value, anchored at {@code anchor}
     * so the {@code int} class is resolved through that element's builtin cache. Returns {@code null}
     * when {@code int} cannot be resolved (e.g. broken interpreter).
     */
    public static @Nullable PyLiteralType intLiteral(PsiElement anchor, BigInteger value) {
        PyClassType intType = PyBuiltinCache.getInstance(anchor).getIntType();
        PyClass intClass = intType != null ? intType.getPyClass() : null;
        return intClass != null ? new PyLiteralType(intClass, new PyLiteralValue.IntValue(value)) : null;
    }

    /**
     * Build a {@code Literal[True]} or {@code Literal[False]} type from a raw {@link Boolean} value, anchored
     * at {@code anchor} so the {@code bool} class is resolved through that element's builtin cache.
     * Returns {@code null} when {@code bool} cannot be resolved.
     */
    public static @Nullable PyLiteralType boolLiteral(PsiElement anchor, boolean value) {
        PyClassType boolType = PyBuiltinCache.getInstance(anchor).getBoolType();
        PyClass boolClass = boolType != null ? boolType.getPyClass() : null;
        return boolClass != null ? new PyLiteralType(boolClass, new PyLiteralValue.BoolValue(value)) : null;
    }

    /**
     * Build a {@link PyLiteralType} from a raw deserialized value whose runtime type
     * determines the literal kind ({@code Boolean} → bool, {@code Number} → int, {@code String} → str).
     * Returns {@code null} when the value type is unsupported or the backing class cannot
     * be resolved.
     */
    public static @Nullable PyLiteralType fromLiteralValue(PsiElement anchor, Object value) {
        if (value instanceof Boolean booleanValue) {
            return boolLiteral(anchor, booleanValue);
        }
        if (value instanceof Number number) {
            return intLiteral(anchor, BigInteger.valueOf(number.longValue()));
        }
        if (value instanceof String string) {
            return stringLiteral(anchor, string);
        }
        return null;
    }

    /**
     * Build a {@link PyLiteralType} from a {@link PyLiteralKind} and the literal's textual value, as stored in the target
     * expression stub or produced by {@code PyTargetExpressionImpl.getAssignedLiteralValueText}. Returns {@code null} for kinds
     * that don't carry a literal type ({@code FLOAT}/{@code NONE}) or when the backing class cannot be resolved. Keeps stub-based
     * and AST-based literal inference in sync.
     */
    public static @Nullable PyLiteralType fromLiteralKind(PsiElement anchor, PyLiteralKind kind, String valueText) {
        return switch (kind) {
            case INT -> intLiteral(anchor, new BigInteger(valueText));
            case STRING -> stringLiteral(anchor, valueText);
            case BOOL -> boolLiteral(anchor, Boolean.parseBoolean(valueText));
            case FLOAT, NONE -> null;
        };
    }

    public static @Nullable PyType upcastLiteralToClass(@Nullable PyType type) {
        if (type instanceof PyUnionType unionType) {
            return unionType.map(PyLiteralType::upcastLiteralToClass);
        }
        if (type instanceof PyLiteralStringType literalStringType) {
            return new PyClassTypeImpl(literalStringType.getCls(), false);
        }
        if (type instanceof PyLiteralType literalType) {
            return new PyClassTypeImpl(literalType.getPyClass(), false);
        }
        return type;
    }

    /**
     * Like {@link #upcastLiteralToClass}, but also widens literal types nested inside generic types, callables and tuples
     * (e.g. {@code Callable[..., Literal[42]]} -> {@code Callable[..., int]}, {@code Generator[Literal["s"], Any, Literal[1]]} ->
     * {@code Generator[str, Any, int]}). Use when generating a declaration-friendly type annotation for an inferred type.
     */
    public static @Nullable PyType upcastLiteralToClassDeep(@Nullable PyType type, TypeEvalContext context) {
        return PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(context) {
            @Override
            public PyType visitPyLiteralType(PyLiteralType literalType) {
                return new PyClassTypeImpl(literalType.getPyClass(), false);
            }

            @Override
            public PyType visitPyLiteralStringType(PyLiteralStringType literalStringType) {
                return new PyClassTypeImpl(literalStringType.getCls(), false);
            }
        });
    }

    private static final class TypePromoter {
        private final TypeEvalContext context;
        private final boolean inferLiteralTypes;

        private TypePromoter(TypeEvalContext context, boolean inferLiteralTypes) {
            this.context = context;
            this.inferLiteralTypes = inferLiteralTypes;
        }

        @Nullable PyType promoteToType(@Nullable PyType expectedType, PyExpression expression) {
            PyExpression value = PyUtil.peelArgument(expression);
            if (value == null) {
                return PyAnyType.getUnknown();
            }
            if (value instanceof PyDictLiteralExpression dictLiteral) {
                return promoteDictLiteral(expectedType, dictLiteral);
            }
            else if (value instanceof PyDictCompExpression dictComp) {
                List<PyKeyValueExpression> elements = new ArrayList<>();
                if (dictComp.getResultExpression() instanceof PyKeyValueExpression keyValue) {
                    elements.add(keyValue);
                }
                return promoteDictLiteralOrDictComprehension(expectedType, dictComp, elements);
            }
            else if (value instanceof PyTupleExpression tupleExpression) {
                return promoteTuple(tupleExpression);
            }
            else if (value instanceof PySetLiteralExpression setLiteral) {
                return promoteListOrSet(expectedType, setLiteral, Arrays.asList(setLiteral.getElements()), PyNames.SET); // TODO: PY-90855
            }
            else if (value instanceof PySetCompExpression setComp) {
                return promoteListOrSet(expectedType, setComp, listOfNotNull(setComp.getResultExpression()), PyNames.SET);
            }
            else if (value instanceof PyListLiteralExpression listLiteral) {
                return promoteListOrSet(expectedType, listLiteral, Arrays.asList(listLiteral.getElements()), "list"); // TODO: PY-90855
            }
            else if (value instanceof PyListCompExpression listComp) {
                return promoteListOrSet(expectedType, listComp, listOfNotNull(listComp.getResultExpression()), "list");
            }
            else {
                PyType type = inferLiteralTypes ? getLiteralOrLiteralStringType(value, context) : null;
                return type != null ? type : context.getType(value);
            }
        }

        private static List<PyExpression> listOfNotNull(@Nullable PyExpression expression) {
            return expression != null ? List.of(expression) : List.of();
        }

        private @Nullable PyType promoteDictLiteral(@Nullable PyType expectedType, PyDictLiteralExpression dictLiteral) {
            for (PyType candidate : PyTypeUtil.asUnionSequence(expectedType)) {
                if (candidate instanceof PyTypedDictType typedDictType) {
                    PyTypedDictType.TypeCheckingResult typeCheckingResult = new PyTypedDictType.TypeCheckingResult();
                    PyTypedDictType.checkExpression(typedDictType, dictLiteral, context, typeCheckingResult);
                    if (!typeCheckingResult.getHasErrors()) {
                        return candidate;
                    }
                }
                else if (candidate instanceof PyClassType classType && !classType.isDefinition() && acceptsDictLiteral(classType, dictLiteral)) {
                    return candidate;
                }
            }
            return promoteDictLiteralOrDictComprehension(expectedType, dictLiteral, Arrays.asList(dictLiteral.getElements()));
        }

        private boolean acceptsDictLiteral(PyClassType expectedType, PyDictLiteralExpression dictLiteral) {
            return PyDictLiteralPromotionExtension.EP_NAME.findFirstSafe(
                it -> it.acceptsDictLiteral(expectedType, dictLiteral, this::promoteToType, context)
            ) != null;
        }

        private @Nullable PyType promoteDictLiteralOrDictComprehension(@Nullable PyType expectedType,
                                                                       PyElement anchor,
                                                                       Collection<PyKeyValueExpression> elements) {
            PyType expectedKeyType;
            PyType expectedValueType;
            if (expectedType instanceof PyClassType classType &&
                PyNames.DICT.equals(PyNames.FQN.unqualifyBuiltinName(classType.getClassQName())) &&
                classType.getTypeArguments().size() >= 2) {
                expectedKeyType = classType.getTypeArguments().get(0);
                expectedValueType = classType.getTypeArguments().get(1);
            }
            else {
                expectedKeyType = null;
                expectedValueType = null;
            }
            List<@Nullable PyType> keyTypes = new ArrayList<>();
            for (PyKeyValueExpression element : elements) {
                keyTypes.add(promoteToType(expectedKeyType, element.getKey()));
            }
            PyType keyType = PyUnionType.unionOrUnknown(keyTypes);
            List<@Nullable PyType> valueTypes = new ArrayList<>();
            for (PyKeyValueExpression keyValue : elements) {
                PyExpression valueExpression = keyValue.getValue();
                if (valueExpression != null) {
                    PyType valueType = promoteToType(expectedValueType, valueExpression);
                    if (valueType != null) {
                        valueTypes.add(valueType);
                    }
                }
            }
            PyType valueType = PyUnionType.unionOrUnknown(valueTypes);
            PyType result = PyCollectionTypeImpl.createTypeByQName(anchor, PyNames.DICT, false, Arrays.asList(keyType, valueType));
            return result != null ? result : PyAnyType.getUnknown();
        }

        private @Nullable PyType promoteTuple(PyTupleExpression tupleExpression) {
            // Per-element promotion would collapse a starred element to a single `Any` slot, so defer to regular
            // inference, which expands the star while still inferring literals for the fixed elements.
            if (Arrays.stream(tupleExpression.getElements()).anyMatch(it -> it instanceof PyStarExpression)) {
                return context.getType(tupleExpression);
            }
            List<@Nullable PyType> elementTypes = new ArrayList<>();
            for (PyExpression element : tupleExpression.getElements()) {
                elementTypes.add(promoteToType(/*TODO*/null, element));
            }
            PyType result = PyTupleType.create(tupleExpression, elementTypes);
            return result != null ? result : PyAnyType.getUnknown();
        }

        private @Nullable PyType promoteListOrSet(@Nullable PyType expectedType,
                                                  PyExpression expr,
                                                  Collection<PyExpression> elements,
                                                  String className) {
            PyType expectedElementType;
            if (expectedType instanceof PyClassType classType &&
                className.equals(PyNames.FQN.unqualifyBuiltinName(classType.getClassQName())) &&
                classType.isParameterized()) {
                List<PyType> typeArguments = classType.getTypeArguments();
                expectedElementType = typeArguments.isEmpty() ? null : typeArguments.get(0);
            }
            else {
                expectedElementType = null;
            }
            List<@Nullable PyType> elementTypes = new ArrayList<>();
            for (PyExpression element : elements) {
                elementTypes.add(promoteToType(expectedElementType, element));
            }
            PyType elementType = PyUnionType.unionOrUnknown(elementTypes);
            PyType result = PyCollectionTypeImpl.createTypeByQName(expr, className, false, Arrays.asList(elementType));
            return result != null ? result : PyAnyType.getUnknown();
        }
    }

    /**
     * {@code actual} matches {@code expected} if it has the same type and its value equals
     */
    public static boolean match(PyLiteralType expected, PyLiteralType actual) {
        return expected.getPyClass().equals(actual.getPyClass()) && expected.value.equals(actual.value);
    }

    /**
     * If {@code expected} type is {@code typing.Literal[...]},
     * then tries to infer {@code typing.Literal[...]} for {@code expression},
     * otherwise returns type inferred by {@code context}.
     */
    public static @Nullable PyType promoteToLiteral(PyExpression expression,
                                                    @Nullable PyType expected,
                                                    TypeEvalContext context,
                                                    PyTypeChecker.@Nullable GenericSubstitutions substitutions) {
        PyType substitution = substitutions != null ? PyTypeChecker.substitute(expected, substitutions, context) : expected;
        PyType substitutionOrBound = substitution instanceof PyTypeVarType typeVarType ? typeVarType.getEffectiveBound() : substitution;
        // PEP 747: a string literal whose contents are a valid type expression is a valid `TypeForm` value.
        if (substitutionOrBound instanceof PyTypeFormType) {
            PyExpression value = PyUtil.peelArgument(expression);
            if (value instanceof PyStringLiteralExpression stringLiteral && !stringLiteral.isInterpolated()) {
                String contents = stringLiteral.getStringValue();
                // A multiline string literal can contain a type expression unparsable without parentheses.
                String toParse = contents.contains("\n") ? "(" + contents + ")" : contents;
                // TODO consulo: Ref.deref missing
                Ref<PyType> denotedRef = PyTypingTypeProvider.getStringBasedType(toParse, stringLiteral, context);
                PyType denoted = denotedRef != null ? denotedRef.get() : null;
                if (denoted != null) {
                    PyTypeFormType typeFormType = PyTypeFormType.create(stringLiteral, denoted);
                    if (typeFormType != null) {
                        return typeFormType;
                    }
                }
            }
        }
        if (isUnknown(substitutionOrBound)) {
            return PyAnyType.getUnknown();
        }
        return new TypePromoter(context, containsLiteral(substitutionOrBound)).promoteToType(substitutionOrBound, expression);
    }

    private static boolean containsLiteral(@Nullable PyType type) {
        return type instanceof PyLiteralType || type instanceof PyLiteralStringType ||
            type instanceof PyUnionType unionType && unionType.getMembers().stream().anyMatch(PyLiteralType::containsLiteral) ||
            type instanceof PyClassType classType && classType.getTypeArguments().stream().anyMatch(PyLiteralType::containsLiteral);
    }

    public static boolean isNone(PyExpression expression) {
        return expression instanceof PyNoneLiteralExpression ||
            expression instanceof PyReferenceExpression &&
                PyNames.NONE.equals(expression.getName()) &&
                LanguageLevel.forElement(expression).isPython2();
    }

    private static @Nullable PyType createFromLiteralParameter(PyExpression expression, TypeEvalContext context, boolean typeRepresentation) {
        if (isNone(expression)) {
            return PyBuiltinCache.getInstance(expression).getNoneType();
        }

        if (expression instanceof PyReferenceExpression || expression instanceof PySubscriptionExpression) {
            // TODO consulo: Ref.deref missing
            Ref<PyType> subLiteralTypeRef = PyTypingTypeProvider.getType(expression, context, typeRepresentation);
            PyType subLiteralType = subLiteralTypeRef != null ? subLiteralTypeRef.get() : null;
            if (PyTypeUtil.toStream(subLiteralType).allMatch(it -> it instanceof PyLiteralType || isNoneType(it))) {
                return subLiteralType;
            }
        }

        return literalType(expression, context, true, typeRepresentation);
    }

    public static @Nullable PyType getLiteralType(PyExpression expression, TypeEvalContext context) {
        if (expression instanceof PyConditionalExpression conditionalExpression) {
            List<@Nullable PyType> types = new ArrayList<>();
            for (PyExpression it : Arrays.asList(conditionalExpression.getTruePart(), conditionalExpression.getFalsePart())) {
                PyType type = it != null ? getLiteralType(it, context) : null;
                types.add(type != null ? type : PyAnyType.getUnknown());
            }
            return PyUnionType.unionOrUnknown(types);
        }
        return literalType(expression, context, false, false);
    }

    private static @Nullable PyType getLiteralOrLiteralStringType(PyExpression expression, TypeEvalContext context) {
        if (expression instanceof PyStringLiteralExpression stringLiteral && stringLiteral.isInterpolated()) {
            boolean allLiteralStringFragments = true;
            for (PyStringElement element : stringLiteral.getStringElements()) {
                if (element instanceof PyFormattedStringElement formattedElement) {
                    for (var fragment : formattedElement.getFragments()) {
                        PyExpression fragmentExpression = fragment.getExpression();
                        if (fragmentExpression != null && !(context.getType(fragmentExpression) instanceof PyLiteralStringType)) {
                            allLiteralStringFragments = false;
                        }
                    }
                }
            }
            if (allLiteralStringFragments) {
                return PyLiteralStringType.create(expression);
            }
        }
        return PyTypeUtilKt.getLiteralType(expression, context);
    }

    private static @Nullable PyLiteralType literalType(PyExpression expression,
                                                       TypeEvalContext context,
                                                       boolean index,
                                                       boolean typeRepresentation) {
        if (expression instanceof PyReferenceExpression referenceExpression && referenceExpression.isQualified()) {
            PyLiteralType type;
            if (typeRepresentation) {
                type = getEnumMemberType(referenceExpression);
            }
            else {
                type = null;
                for (PsiElement it : PyUtil.multiResolveTopPriority(referenceExpression, PyResolveContext.defaultContext(context))) {
                    type = PyStdlibTypeProvider.getEnumMemberType(it, context);
                    if (type != null) {
                        break;
                    }
                }
                // When regular reference resolution yields nothing because the reference lives in a stub-only file rather than
                // the one being analyzed (e.g. an overload signature `Literal[E.a]` in an imported module), fall back to
                // stub-safe scope resolution of the enum member's qualified name. Without this, the literal type is lost and
                // overload resolution can't tell the overloads apart. PY-42473.
                if (type == null) {
                    type = resolveEnumMemberInScope(referenceExpression, context);
                }
            }
            if (type != null) {
                return type;
            }
        }
        PyClass cls = classOfAcceptableLiteral(expression, context, index);
        if (cls == null) {
            return null;
        }
        PyLiteralValue value = extractLiteralValue(expression);
        if (value == null) {
            return null;
        }
        return new PyLiteralType(cls, value);
    }

    private static @Nullable PyLiteralType getEnumMemberType(PyReferenceExpression typeRepresentationRef) {
        PsiFile contextFile = FileContextUtil.getContextFile(typeRepresentationRef);
        if (contextFile == null) {
            return null;
        }
        QualifiedName qualifiedName = typeRepresentationRef.asQualifiedName();
        if (qualifiedName == null) {
            return null;
        }
        List<PsiElement> resolved = PyPsiFacadeImpl.resolveQName(qualifiedName.removeLastComponent(), contextFile);
        if (resolved.size() != 1 || !(resolved.get(0) instanceof PyClass enumClass)) {
            return null;
        }
        String enumMemberName = qualifiedName.getLastComponent();
        if (enumMemberName == null) {
            return null;
        }
        return enumMember(enumClass, enumMemberName);
    }

    private static @Nullable PyLiteralType resolveEnumMemberInScope(PyReferenceExpression reference, TypeEvalContext context) {
        QualifiedName qualifiedName = reference.asQualifiedName();
        if (qualifiedName == null) {
            return null;
        }
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(reference);
        if (scopeOwner == null) {
            return null;
        }
        for (PsiElement it : PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context)) {
            PyLiteralType type = PyStdlibTypeProvider.getEnumMemberType(it, context);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    private static @Nullable PyLiteralValue extractLiteralValue(PyExpression expression) {
        if (expression instanceof PyNumericLiteralExpression numericLiteral && numericLiteral.isIntegerLiteral()) {
            return new PyLiteralValue.IntValue(numericLiteral.getBigIntegerValue());
        }
        else if (expression instanceof PyStringLiteralExpression stringLiteral) {
            return new PyLiteralValue.StringValue(stringLiteral.getStringValue(), stringLiteral.getText());
        }
        else if (expression instanceof PyPrefixExpression prefixExpression) {
            if (!(prefixExpression.getOperand() instanceof PyNumericLiteralExpression operand)) {
                return null;
            }
            if (!operand.isIntegerLiteral()) {
                return null;
            }
            BigInteger bigInt = operand.getBigIntegerValue();
            if (prefixExpression.getOperator() == PyTokenTypes.MINUS) {
                return new PyLiteralValue.IntValue(bigInt.negate());
            }
            else {
                return new PyLiteralValue.IntValue(bigInt);
            }
        }
        else {
            Boolean boolVal = PyEvaluator.getBooleanLiteralValue(expression);
            return boolVal != null ? new PyLiteralValue.BoolValue(boolVal) : null;
        }
    }

    private static @Nullable PyClass classOfAcceptableLiteral(PyExpression expression, TypeEvalContext context, boolean index) {
        if (expression instanceof PyNumericLiteralExpression numericLiteral) {
            return numericLiteral.isIntegerLiteral() ? getPyClass(expression, context) : null;
        }
        else if (expression instanceof PyStringLiteralExpression stringLiteral) {
            return isAcceptableStringLiteral(stringLiteral, index) ? getPyClass(expression, context) : null;
        }
        else if (expression instanceof PyEllipsisLiteralExpression || expression instanceof PyNoneLiteralExpression) {
            return null;
        }
        else if (expression instanceof PyLiteralExpression) {
            return getPyClass(expression, context);
        }
        else if (expression instanceof PyPrefixExpression prefixExpression &&
            (prefixExpression.getOperator() == PyTokenTypes.PLUS || prefixExpression.getOperator() == PyTokenTypes.MINUS)) {
            PyExpression operand = prefixExpression.getOperand();
            return operand instanceof PyNumericLiteralExpression numericOperand && numericOperand.isIntegerLiteral()
                ? getPyClass(operand, context)
                : null;
        }
        else {
            if (PyEvaluator.getBooleanLiteralValue(expression) != null) {
                return getPyClass(expression, context);
            }
            else {
                return null;
            }
        }
    }

    private static @Nullable PyClass getPyClass(PyExpression expression, TypeEvalContext context) {
        return context.getType(expression) instanceof PyClassType classType ? classType.getPyClass() : null;
    }

    private static boolean isAcceptableStringLiteral(PyStringLiteralExpression expression, boolean index) {
        List<PyStringElement> stringElements = expression.getStringElements();
        if (stringElements.isEmpty()) {
            return false;
        }

        PyStringElement singleElement = stringElements.size() == 1 ? stringElements.get(0) : null;
        if (!index && singleElement instanceof PyFormattedStringElement formattedElement) {
            return formattedElement.getFragments().isEmpty();
        }
        return stringElements.stream().allMatch(it -> it instanceof PyPlainStringElement);
    }
}
