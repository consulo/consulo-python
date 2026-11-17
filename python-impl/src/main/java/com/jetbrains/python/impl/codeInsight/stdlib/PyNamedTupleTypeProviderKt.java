// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.impl.psi.impl.stubs.PyNamedTupleStubImpl;
import com.jetbrains.python.impl.psi.stubs.PyNamedTupleStub;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.types.*;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Top-level functions of PyNamedTupleTypeProvider.kt
 */
public final class PyNamedTupleTypeProviderKt {
    private PyNamedTupleTypeProviderKt() {
    }

    /**
     * private typealias NTFields = LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue>
     */
    private static final class NTFields extends LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue> {
    }

    static @Nullable PyType getFieldTypeForNamedTupleAsTarget(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        PyExpression qualifier = referenceExpression.getQualifier();
        if (!((qualifier != null ? context.getType(qualifier) : null) instanceof PyNamedTupleType qualifierNTType)) {
            return null;
        }
        PyNamedTupleType.FieldTypeAndDefaultValue field = qualifierNTType.getFields().get(referenceExpression.getName());
        return field != null ? field.getType() : null;
    }

    static @Nullable PyType getFieldTypeForTypingNTFunctionInheritor(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        PyExpression qualifier = referenceExpression.getQualifier();
        PyType type = qualifier != null ? context.getType(qualifier) : null;
        PyWithAncestors qualifierType = type instanceof PyWithAncestors withAncestors ? withAncestors : null;
        if (qualifierType == null || qualifierType instanceof PyNamedTupleType) {
            return null;
        }

        List<@Nullable PyType> fieldTypes = new ArrayList<>();
        for (PyClassLikeType ancestor : qualifierType.getAncestorTypes(context)) {
            if (ancestor instanceof PyNamedTupleType namedTupleType) {
                PyNamedTupleType.FieldTypeAndDefaultValue field = namedTupleType.getFields().get(referenceExpression.getName());
                if (field != null) {
                    fieldTypes.add(field.getType());
                }
            }
        }
        if (fieldTypes.isEmpty()) {
            return null;
        }
        return PyUnionType.unionOrUnknown(fieldTypes);
    }

    /**
     * Parameterizes a generic named tuple with {@code typeArguments}.
     * <p>
     * The function maps the type parameters of the class to {@code typeArguments}. It keeps the result as the type arguments
     * of the named tuple, and rewrites each field type. For `class Base(NamedTuple, Generic[T])` with the field `path: T`,
     * `Base[str]` gets the type argument `str` and the field `path: str`.
     * <p>
     * The function returns {@code null} when the class is not generic, or when the type arguments do not match the
     * type parameters.
     */
    public static @Nullable PyNamedTupleType parameterizeNamedTupleType(
        PyNamedTupleType type,
        List<@Nullable PyType> typeArguments,
        TypeEvalContext context
    ) {
        PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(type.getPyClass(), context);
        if (genericDefinitionType == null) {
            return null;
        }
        PyTypeChecker.GenericSubstitutions substitutions = PyTypeChecker.mapTypeParametersToSubstitutions(
            genericDefinitionType.getTypeArguments(),
            typeArguments,
            PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
            PyTypeParameterMapping.Option.USE_DEFAULTS
        );
        if (substitutions == null) {
            return null;
        }
        if (!(PyTypeChecker.substitute(genericDefinitionType, substitutions, context) instanceof PyClassType substitutedClassType)) {
            return null;
        }
        List<PyType> classTypeArguments = substitutedClassType.getTypeArguments();
        return type.mapTypes(
            // A field without a type parameter keeps its type, because a parameterization must not widen the declared type of a field.
            fieldType -> PyTypeChecker.hasGenerics(fieldType, context) ? PyTypeChecker.substitute(fieldType, substitutions, context) : fieldType,
            it -> classTypeArguments
        );
    }

    /**
     * The constructor signature of the generic named tuple class {@code type}: the fields as parameters, and the named tuple
     * parameterized with its own type parameters as the return type. The generic call then binds the type parameters,
     * as for a dataclass, so `Base(42)` of `class Base[T](NamedTuple)` with the field `foo: T` gives `Base[int]`.
     * <p>
     * Returns {@code null} when {@code type} is not the definition of a typed generic named tuple, or is already parameterized.
     */
    static @Nullable PyCallableType generateGenericConstructorType(PyNamedTupleType type, TypeEvalContext context) {
        if (!type.isDefinition() || !type.isTyped() || !type.getTypeArguments().isEmpty()) {
            return null;
        }
        PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(type.getPyClass(), context);
        if (genericDefinitionType == null) {
            return null;
        }
        List<PyType> typeParameters = genericDefinitionType.getTypeArguments();
        PyNamedTupleType returnType = parameterizeNamedTupleType(type.toInstance(), typeParameters, context);
        if (returnType == null) {
            return null;
        }
        return new PyCallableTypeImpl(type.getParameters(context), returnType);
    }

    static @Nullable PyCallableType getNamedTupleReplaceType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        PyCallExpression call = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression);
        if (call == null) {
            return null;
        }

        PyExpression qualifier = referenceExpression.getQualifier();
        if (qualifier != null && "_replace".equals(referenceExpression.getReferencedName())) {
            if (!(context.getType(qualifier) instanceof PyClassLikeType qualifierType)) {
                return null;
            }

            return getCallableType(qualifierType, context, call);
        }

        return null;
    }

    static @Nullable PyCallableType getCallableType(
        PyClassLikeType qualifierType,
        TypeEvalContext context,
        PsiElement anchor
    ) {
        List<PyClassLikeType> candidates = new ArrayList<>();
        candidates.add(qualifierType);
        candidates.addAll(qualifierType.getSuperClassTypes(context));
        PyNamedTupleType namedTupleType = null;
        for (PyClassLikeType it : candidates) {
            if (it instanceof PyNamedTupleType ntType) {
                namedTupleType = ntType;
                break;
            }
        }

        if (namedTupleType != null) {
            return namedTupleType.isTyped() ? createTypedNamedTupleReplaceType(anchor, namedTupleType.getFields(), qualifierType)
                : createUntypedNamedTupleReplaceType(anchor, namedTupleType.getFields(), qualifierType, context);
        }

        if (qualifierType instanceof PyClassType classType) {
            PyClass cls = classType.getPyClass();
            if (PyNamedTupleTypeProvider.Helper.isTypingNamedTupleDirectInheritor(cls, context)) {
                return createTypedNamedTupleReplaceType(anchor, collectTypingNTInheritorFields(cls, context), qualifierType);
            }
        }
        return null;
    }

    static @Nullable PyType getNamedTupleFunctionType(PyFunction function, TypeEvalContext context, PyCallExpression call) {
        boolean isCollectionsNamedTuple =
            ArrayUtil.contains(function.getQualifiedName(), PyNames.COLLECTIONS_NAMEDTUPLE_PY2, PyNames.COLLECTIONS_NAMEDTUPLE_PY3);
        PyClass constructedClass = PyUtil.turnConstructorIntoClass(function);
        boolean isTypingNamedTupleInit = !isCollectionsNamedTuple &&
                                         PyTypingTypeProvider.NAMEDTUPLE.equals(constructedClass != null ? constructedClass.getQualifiedName() : null);
        if (!isCollectionsNamedTuple && !isTypingNamedTupleInit) {
            return null;
        }
        if (!context.maySwitchToAST(call)) {
            return null;
        }

        PyNamedTupleType returnType = getNamedTupleTypeFromStub(call, PyNamedTupleStubImpl.create(call), context);
        if (returnType == null) {
            return null;
        }

        // `typing.NamedTuple.__init__` is overloaded; build a signature per overload and keep them as a single `PyOverloadType`
        // (matching any signature is enough), rather than a union of signatures, which would force every signature to match the call.
        List<PyFunction> constructors = null;
        if (isTypingNamedTupleInit) {
            PyClass containingClass = function.getContainingClass();
            List<PyFunction> initOrNew = containingClass != null ? containingClass.multiFindInitOrNew(false, context) : null;
            constructors = initOrNew != null && !initOrNew.isEmpty() ? initOrNew : null;
        }
        List<PyCallableType> signatures = new ArrayList<>();
        for (PyFunction constructor : constructors != null ? constructors : List.of(function)) {
            if (!(context.getType(constructor) instanceof PyCallableType constructorType)) {
                continue;
            }
            List<PyCallableParameter> parameters = constructorType.getParameters(context);
            signatures.add(new PyCallableTypeImpl(
                isTypingNamedTupleInit && parameters != null ? parameters.subList(Math.min(1, parameters.size()), parameters.size()) : parameters,
                returnType,
                constructorType.getCallable(),
                constructorType.getModifier()
            ));
        }
        return switch (signatures.size()) {
            case 0 -> null;
            case 1 -> signatures.get(0);
            default -> new PyOverloadType(signatures, null);
        };
    }

    static @Nullable PyNamedTupleType getNamedTupleTypeForTarget(PyTargetExpression target, TypeEvalContext context) {
        return StubAwareComputation.on(target)
            .withCustomStub(it -> it.getCustomStub(PyNamedTupleStub.class))
            .overStub(it -> getNamedTupleTypeFromStub(target, it, context))
            .withStubBuilder(it -> PyNamedTupleStubImpl.create(it))
            .compute(context);
    }

    static @Nullable PyType getNamedTupleTypeForClass(PyClass cls, TypeEvalContext context, PyCallExpression call) {
        PyNamedTupleType type = getNamedTupleTypeForClass(cls, context);
        if (type != null) {
            return type;
        }
        List<PyFunction> initOrNew = cls.multiFindInitOrNew(false, context);
        return !initOrNew.isEmpty() ? getNamedTupleFunctionType(initOrNew.get(0), context, call) : null;
    }

    public static @Nullable PyNamedTupleType getNamedTupleTypeForClass(PyClass cls, TypeEvalContext context) {
        // Decide whether the class can be a named tuple at all before asking whether it declares `__init__`.
        // All three conditions below are necessary and each is a pure predicate, so the order does not change the result,
        // but `findInitOrNew` is far the most expensive of them: it walks the methods of the class, asks
        // `PyiUtil.isOverload` about the ones whose name matches, and each of those rescans the import statements of the
        // whole containing file (`PyKnownDecoratorUtil.computeKnownDecoratorsInFile`). This provider runs for every class
        // type evaluation, so for a class that has nothing to do with `NamedTuple` - any `pandas` class, say - that walk
        // was pure waste.
        boolean isDirectInheritor = PyNamedTupleTypeProvider.Helper.isTypingNamedTupleDirectInheritor(cls, context);
        PyNamedTupleType namedTupleBase = null;
        if (!isDirectInheritor) {
            for (PyClassLikeType it : cls.getSuperClassTypes(context)) {
                if (it instanceof PyNamedTupleType ntType) {
                    namedTupleBase = ntType;
                    break;
                }
            }
            if (namedTupleBase == null) {
                return null;
            }
        }

        if (cls.findInitOrNew(false, context) != null) {
            return null;
        }

        String name = cls.getName();
        if (name == null) {
            return null;
        }

        if (isDirectInheritor) {
            return new PyNamedTupleType(cls, name, collectTypingNTInheritorFields(cls, context), true, true, cls);
        }
        else {
            return new PyNamedTupleType(cls, name, new LinkedHashMap<>(Objects.requireNonNull(namedTupleBase).getFields()), true, true, cls);
        }
    }

    private static @Nullable PyNamedTupleType getNamedTupleTypeFromStub(PsiElement targetOrCall,
                                                                        @Nullable PyNamedTupleStub stub,
                                                                        TypeEvalContext context) {
        if (stub == null) {
            return null;
        }

        PyClass tupleClass = PyPsiFacade
            .getInstance(targetOrCall.getProject())
            .createClassByQName(PyTypingTypeProvider.NAMEDTUPLE, targetOrCall);
        if (tupleClass == null) {
            return null;
        }
        LinkedHashMap<String, PyNamedTupleStub.FieldTypeAndHasDefault> fields = stub.getFields();

        return new PyNamedTupleType(tupleClass,
                                    stub.getName(),
                                    parseNamedTupleFields(targetOrCall, fields, context),
                                    true,
                                    fields.values().stream().anyMatch(it -> it.type() != null),
                                    getDeclaration(targetOrCall));
    }

    private static PyCallableType createTypedNamedTupleReplaceType(
        PsiElement anchor,
        Map<String, PyNamedTupleType.FieldTypeAndDefaultValue> fields,
        PyClassLikeType qualifierType
    ) {
        List<PyCallableParameter> parameters = new ArrayList<>();
        PyClassLikeType resultType = qualifierType.toInstance();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(anchor.getProject());

        if (qualifierType.isDefinition()) {
            parameters.add(PyCallableParameterImpl.nonPsi(PyNames.CANONICAL_SELF, resultType));
        }
        parameters.add(PyCallableParameterImpl.keywordOnlySeparatorNonPsi());

        PyEllipsisLiteralExpression ellipsis = elementGenerator.createEllipsis();

        for (Map.Entry<String, PyNamedTupleType.FieldTypeAndDefaultValue> entry : fields.entrySet()) {
            PyNamedTupleType.FieldTypeAndDefaultValue typeAndValue = entry.getValue();
            PyExpression defaultValue = typeAndValue.getDefaultValue();
            parameters.add(PyCallableParameterImpl.nonPsi(entry.getKey(), typeAndValue.getType(), defaultValue != null ? defaultValue : ellipsis));
        }

        return new PyCallableTypeImpl(parameters, resultType);
    }

    private static @Nullable PyCallableType createUntypedNamedTupleReplaceType(
        PsiElement anchor,
        Map<String, PyNamedTupleType.FieldTypeAndDefaultValue> fields,
        PyClassLikeType qualifierType,
        TypeEvalContext context
    ) {
        List<PyCallableParameter> parameters = new ArrayList<>();
        PyClassLikeType resultType = qualifierType.toInstance();

        if (qualifierType.isDefinition()) {
            parameters.add(PyCallableParameterImpl.nonPsi(PyNames.CANONICAL_SELF, resultType));
        }
        parameters.add(PyCallableParameterImpl.keywordOnlySeparatorNonPsi());

        for (String it : fields.keySet()) {
            parameters.add(PyCallableParameterImpl.nonPsi(it, PyAnyType.getUnknown(), PyNames.ELLIPSIS));
        }

        if (resultType instanceof PyNamedTupleType namedTupleType && anchor instanceof PyCallExpression call) {
            Map<String, PyType> newFields = new HashMap<>();

            for (PyExpression argument : call.getArguments()) {
                if (argument instanceof PyKeywordArgument keywordArgument) {
                    PyExpression value = keywordArgument.getValueExpression();
                    if (value != null) {
                        newFields.put(keywordArgument.getKeyword(), context.getType(value));
                    }
                }
            }

            return new PyCallableTypeImpl(parameters, namedTupleType.clarifyFields(newFields));
        }
        else {
            return new PyCallableTypeImpl(parameters, resultType);
        }
    }

    private static NTFields collectTypingNTInheritorFields(PyClass cls, TypeEvalContext context) {
        List<PyTargetExpression> fields = new ArrayList<>();

        cls.processClassLevelDeclarations((element, state) -> {
            if (element instanceof PyTargetExpression targetExpression && targetExpression.getAnnotationValue() != null) {
                fields.add(targetExpression);
            }

            return true;
        });

        PyEllipsisLiteralExpression ellipsis = PyElementGenerator.getInstance(cls.getProject()).createEllipsis();

        NTFields result = new NTFields();
        for (PyTargetExpression field : fields) {
            PyExpression value;
            if (context.maySwitchToAST(field)) {
                value = field.findAssignedValue();
            }
            else if (field.hasAssignedValue()) {
                value = ellipsis;
            }
            else {
                value = null;
            }

            result.put(Objects.requireNonNull(field.getName()), new PyNamedTupleType.FieldTypeAndDefaultValue(context.getType(field), value));
        }
        return result;
    }

    private static NTFields parseNamedTupleFields(
        PsiElement anchor,
        LinkedHashMap<String, PyNamedTupleStub.FieldTypeAndHasDefault> fields,
        TypeEvalContext context
    ) {
        NTFields result = new NTFields();
        for (Map.Entry<String, PyNamedTupleStub.FieldTypeAndHasDefault> entry : fields.entrySet()) {
            PyNamedTupleStub.FieldTypeAndHasDefault typeAndDefault = entry.getValue();
            String type = typeAndDefault.type();
            PyType pyType = null;
            if (type != null) {
                // TODO consulo: Ref.deref missing
                Ref<PyType> typeRef = PyTypingTypeProvider.getStringBasedType(type, anchor, context);
                pyType = typeRef != null ? typeRef.get() : null;
            }
            if (pyType == null) {
                pyType = PyAnyType.getUnknown();
            }
            PyExpression defaultValue = typeAndDefault.hasDefault() ? PyElementGenerator.getInstance(anchor.getProject()).createEllipsis() : null;
            result.put(entry.getKey(), new PyNamedTupleType.FieldTypeAndDefaultValue(pyType, defaultValue));
        }
        return result;
    }

    private static @Nullable PyQualifiedNameOwner getDeclaration(PsiElement referenceTarget) {
        if (referenceTarget instanceof PyTargetExpression targetExpression) {
            return targetExpression;
        }
        else if (referenceTarget instanceof PyCallExpression callExpression) {
            return callExpression.getParent() instanceof PyAssignmentStatement assignmentStatement &&
                   assignmentStatement.getLeftHandSideExpression() instanceof PyTargetExpression targetExpression
                ? targetExpression
                : null;
        }
        else {
            return null;
        }
    }
}
