// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyTypeChecker.GenericSubstitutions;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PyCollectionTypeUtil {
    private PyCollectionTypeUtil() {
    }

    private static final int MAX_ANALYZED_ELEMENTS_OF_LITERALS = 10 /* performance */;

    public static @Nullable PyType getListLiteralType(PyListLiteralExpression expression, TypeEvalContext context) {
        return getSequenceLiteralType(expression, context);
    }

    public static @Nullable PyType getSetLiteralType(PySetLiteralExpression expression, TypeEvalContext context) {
        return getSequenceLiteralType(expression, context);
    }

    public static @Nullable PyType getTupleLiteralType(PyTupleExpression expression, TypeEvalContext context) {
        return getSequenceLiteralType(expression, context);
    }

    private static @Nullable PyType getSequenceLiteralType(PySequenceExpression expression, TypeEvalContext context) {
        PyClassType genericType = findGenericDefinitionTypeOfSequence(expression, context);
        if (genericType == null) {
            return PyAnyType.getUnknown();
        }
        GenericSubstitutions substitutions = PyTypeInferenceCspFactory.unifySequenceExpression(expression, context);
        PyType concreteType = PyTypeChecker.substitute(genericType, substitutions, context);
        return concreteType;
    }

    static GenericSubstitutions getSequenceSubstitutionsFallback(PySequenceExpression sequence, TypeEvalContext context) {
        PyClassType genericType = findGenericDefinitionTypeOfSequence(sequence, context);
        if (genericType == null) {
            return new GenericSubstitutions();
        }
        if (sequence instanceof PyListLiteralExpression || sequence instanceof PySetLiteralExpression) {
            List<PyType> typeArguments = genericType.getTypeArguments();
            if (!(!typeArguments.isEmpty() && typeArguments.get(0) instanceof PyTypeParameterType typeVar)) {
                return new GenericSubstitutions();
            }
            PyType typeArgument = getListOrSetIteratedValueType(sequence, context);
            Map<PyTypeParameterType, @Nullable PyType> map = new LinkedHashMap<>();
            map.put(typeVar, typeArgument);
            return new GenericSubstitutions(map);
        }
        else if (sequence instanceof PyTupleExpression) {
            List<@Nullable PyType> typeArgs = getTupleElementTypes(sequence, context);
            List<PyTypeParameterType> typeParams = new ArrayList<>();
            for (PyType typeArgument : genericType.getTypeArguments()) {
                if (typeArgument instanceof PyTypeParameterType typeParameterType) {
                    typeParams.add(typeParameterType);
                }
            }
            Map<PyTypeParameterType, @Nullable PyType> typeParamsMap = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(typeParams.size(), typeArgs.size()); i++) {
                typeParamsMap.put(typeParams.get(i), typeArgs.get(i));
            }
            return new GenericSubstitutions(typeParamsMap);
        }
        else {
            return new GenericSubstitutions();
        }
    }

    static @Nullable PyClassType findGenericDefinitionTypeOfSequence(PySequenceExpression sequence, TypeEvalContext context) {
        String className;
        if (sequence instanceof PyListLiteralExpression) {
            className = PyNames.FQN.LIST;
        }
        else if (sequence instanceof PySetLiteralExpression) {
            className = PyNames.FQN.SET;
        }
        else if (sequence instanceof PyTupleExpression) {
            className = PyNames.FQN.TUPLE;
        }
        else {
            return null;
        }
        PyClass pyClass = PyBuiltinCache.getInstance(sequence).getClass(className);
        if (pyClass == null) {
            return null;
        }
        PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(pyClass, context);
        if (genericDefinitionType == null) {
            return null;
        }

        if (sequence instanceof PyTupleExpression) {
            int flattenedElemsCount = flattenSequenceElements(sequence).size();
            if (flattenedElemsCount == 0) {
                return PyTupleType.create(sequence, Collections.emptyList());
            }
            List<@Nullable PyType> expandedTypeParameters =
                PyTypeChecker.expandTupleTypeParameters(genericDefinitionType.getTypeArguments(), flattenedElemsCount);
            if (expandedTypeParameters == null) {
                expandedTypeParameters = genericDefinitionType.getTypeArguments();
            }
            return PyTupleType.create(sequence, expandedTypeParameters);
        }

        return genericDefinitionType;
    }

    private static @Nullable PyType getListOrSetIteratedValueType(PySequenceExpression sequence, TypeEvalContext context) {
        List<@Nullable PyType> elementTypes = getTupleElementTypes(sequence, context);
        PyTupleType tupleType = PyTupleType.create(sequence, elementTypes);
        PyType iteratedItemType = tupleType != null ? tupleType.getIteratedItemType() : null;
        if (iteratedItemType == null) {
            return PyAnyType.getUnknown();
        }
        if (iteratedItemType instanceof PyNeverType) {
            return PyAnyType.getUnknown();
        }
        // widen since used in invariant list/set
        PyType analyzedElementsType = PyTypeUtil.widenLiteralAndNumeric(iteratedItemType);
        if (sequence.getElements().length > MAX_ANALYZED_ELEMENTS_OF_LITERALS) {
            return PyUnionType.createWeakType(analyzedElementsType);
        }
        else {
            return analyzedElementsType;
        }
    }

    private static List<@Nullable PyType> getTupleElementTypes(PySequenceExpression sequence, TypeEvalContext context) {
        List<PyExpression> flattenedElements = flattenSequenceElements(sequence);
        flattenedElements = flattenedElements.subList(0, Math.min(flattenedElements.size(), MAX_ANALYZED_ELEMENTS_OF_LITERALS));
        List<@Nullable PyType> elementTypes = new ArrayList<>();
        for (PyExpression it : flattenedElements) {
            elementTypes.add(PyTypeInferenceCspFactoryKt.getNarrowedType(context, it));
        }
        return elementTypes;
    }

    static List<PyExpression> flattenSequenceElements(PySequenceExpression expression) {
        List<PyExpression> result = new ArrayList<>();
        collectFlattenedElements(expression.getElements(), result);
        return result;
    }

    private static void collectFlattenedElements(PyExpression[] elements, List<PyExpression> result) {
        for (PyExpression element : elements) {
            PyExpression starOperand = element instanceof PyStarExpression starExpression ? starExpression.getExpression() : null;
            if (starOperand != null) {
                PyExpression unwrappedStarOperand = PyPsiUtils.flattenParens(starOperand);
                // `(1, *(2, 3))` and `(1, *[2, 3])` splice their elements in directly.
                if (unwrappedStarOperand instanceof PyTupleExpression tupleExpression) {
                    collectFlattenedElements(tupleExpression.getElements(), result);
                }
                else if (unwrappedStarOperand instanceof PyListLiteralExpression listLiteral) {
                    collectFlattenedElements(listLiteral.getElements(), result);
                }
                // Any other starred operand cannot be flattened syntactically; keep the star expression as-is.
                else {
                    result.add(element);
                }
            }
            else {
                result.add(element);
            }
        }
    }

    public static @Nullable PyType getDictLiteralType(PyDictLiteralExpression expression, TypeEvalContext context) {
        PyClass cls = PyBuiltinCache.getInstance(expression).getClass("dict");
        if (cls == null) {
            return PyAnyType.getUnknown();
        }
        Pair<@Nullable PyType, @Nullable PyType> elementTypes = getDictLiteralElementTypes(expression, context);
        return new PyCollectionTypeImpl(cls, false, Arrays.asList(elementTypes.getFirst(), elementTypes.getSecond()));
    }

    public static @Nullable Map<String, Pair<@Nullable PyExpression, @Nullable PyType>> getTypedDictFieldsFromDictLiteral(
        PyDictLiteralExpression sequence,
        TypeEvalContext context
    ) {
        Map<String, Pair<@Nullable PyExpression, @Nullable PyType>> strKeysToValueTypes = new LinkedHashMap<>();

        for (PyKeyValueExpression element : sequence.getElements()) {
            PyType elementType = context.getType(element);
            Pair<@Nullable PyType, @Nullable PyType> keyValueType = getKeyValueType(elementType);
            PyType keyType = keyValueType.getFirst();
            PyType valueType = keyValueType.getSecond();

            if (!(keyType instanceof PyClassType keyClassType && PyNames.FQN.STR.equals(keyClassType.getClassQName()))) {
                return null;
            }
            String keyString;
            if (keyType instanceof PyLiteralType literalType) {
                keyString = literalType.getStringValue();
            }
            else {
                PyExpression keyExpression = element.getKey();
                keyString = keyExpression instanceof PyStringLiteralExpression stringLiteral ? stringLiteral.getStringValue() : null;
            }
            if (keyString == null) {
                return null;
            }
            strKeysToValueTypes.put(keyString, Pair.create(element.getValue(), PyTypeUtil.widenLiteralAndNumeric(valueType)));
        }

        return strKeysToValueTypes;
    }

    private static Pair<@Nullable PyType, @Nullable PyType> getDictLiteralElementTypes(PyDictLiteralExpression sequence,
                                                                                       TypeEvalContext context) {
        PyKeyValueExpression[] elements = sequence.getElements();
        List<@Nullable PyType> keyTypes = new ArrayList<>();
        List<@Nullable PyType> valueTypes = new ArrayList<>();

        for (int i = 0; i < Math.min(elements.length, MAX_ANALYZED_ELEMENTS_OF_LITERALS); i++) {
            PyType type = context.getType(elements[i]);
            Pair<@Nullable PyType, @Nullable PyType> keyValueType = getKeyValueType(type);
            keyTypes.add(PyTypeUtil.widenLiteralAndNumeric(keyValueType.getFirst()));
            valueTypes.add(PyTypeUtil.widenLiteralAndNumeric(keyValueType.getSecond()));
        }

        if (elements.length > MAX_ANALYZED_ELEMENTS_OF_LITERALS) {
            keyTypes.add(PyAnyType.getUnknown());
            valueTypes.add(PyAnyType.getUnknown());
        }

        return Pair.create(PyUnionType.unionOrUnknown(keyTypes), PyUnionType.unionOrUnknown(valueTypes));
    }

    private static Pair<@Nullable PyType, @Nullable PyType> getKeyValueType(@Nullable PyType sequenceElementType) {
        if (sequenceElementType instanceof PyTupleType tupleType) {
            if (tupleType.isHomogeneous()) {
                PyType iteratedItemType = tupleType.getIteratedItemType();
                return Pair.create(iteratedItemType, iteratedItemType);
            }
            List<@Nullable PyType> tupleElementTypes = tupleType.getElementTypes();
            if (tupleElementTypes.size() == 2) {
                return Pair.create(tupleElementTypes.get(0), tupleElementTypes.get(1));
            }
        }
        return Pair.create(PyAnyType.getUnknown(), PyAnyType.getUnknown());
    }
}
