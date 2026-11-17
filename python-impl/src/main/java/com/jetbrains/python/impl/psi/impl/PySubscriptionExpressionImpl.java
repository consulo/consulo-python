// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.util.lang.ref.SoftReference;
import consulo.language.ast.ASTNode;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;


public class PySubscriptionExpressionImpl extends PyElementImpl implements PySubscriptionExpression {
    public PySubscriptionExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPySubscriptionExpression(this);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression indexExpression = getIndexExpression();
        if (indexExpression != null) {
            PyType operandType = context.getType(getOperand());
            if (indexExpression instanceof PySliceItem) {
                if (operandType instanceof PyTupleType) {
                    if (((PyTupleType)operandType).isHomogeneous()) {
                        return operandType;
                    }
                    PyClassType tupleType = PyBuiltinCache.getInstance(this).getTupleType();
                    return tupleType != null ? tupleType : PyAnyType.getUnknown();
                }
            }
            else {
                if (operandType instanceof PyTupleType tupleType) {
                    List<Integer> indexPossibleValues = getIndexExpressionPossibleValues(indexExpression, context, Integer.class);
                    List<@Nullable PyType> possibleTypes = ContainerUtil.map(indexPossibleValues, index -> {
                        if (!tupleType.isHomogeneous() && index < 0) {
                            index += tupleType.getElementCount();
                        }
                        return tupleType.getElementType(index);
                    });
                    return PyUnionType.unionOrUnknown(possibleTypes);
                }
                if (operandType instanceof PyTypedDictType typedDictType) {
                    return typedDictType.isDefinition()
                           ? parameterizeTypedDictDeclaration(typedDictType, context)
                           : getTypedDictSubscriptionType(typedDictType, indexExpression, context);
                }
                if (operandType instanceof PyClassType classType &&
                    !(classType.isDefinition() && PyTypeUtil.hasMetaclassGetItem(classType.getPyClass(), context))) {
                    PyType parameterizedType = SoftReference.deref(PyTypingTypeProvider.getType(this, context));
                    if (parameterizedType instanceof PyClassType pyClassType && pyClassType.isParameterized()) {
                        return pyClassType.toClass();
                    }
                }
            }
        }
        return PyCallExpressionHelper.getCallType(this, context, key);
    }

    /**
     * A TypedDict without type parameters cannot be parameterized, and the expression still denotes the class, so the declaration
     * type is returned rather than the `dict` class the generic fallback would answer with.
     */
    private PyType parameterizeTypedDictDeclaration(PyTypedDictType declarationType,
                                                             TypeEvalContext context) {
        PyType parameterizedType = SoftReference.deref(PyTypingTypeProvider.getType(this, context));
        if (parameterizedType instanceof PyTypedDictType typedDictType && typedDictType.isParameterized()) {
            return typedDictType.toClass();
        }
        return declarationType;
    }

    /** The extra items type is asked for only when a key is really missing: evaluating it costs as much as an item type. */
    private static @Nullable PyType getTypedDictSubscriptionType(PyTypedDictType typedDictType,
                                                                 PyExpression indexExpression,
                                                                 TypeEvalContext context) {
        List<String> keys = getIndexExpressionPossibleValues(indexExpression, context, String.class);
        if (keys.isEmpty()) {
            if (typedDictType.isClosed() || !isStringIndex(indexExpression, context)) {
                return PyAnyType.getUnknown();
            }
            PyType extraItemsType = typedDictType.extraItemsType(context);
            if (extraItemsType == null || isUnknown(extraItemsType)) {
                return PyAnyType.getUnknown();
            }
            // Non-literal `str` key: the value can be any declared item or an explicitly typed extra item.
            List<PyType> types =
                new ArrayList<>(ContainerUtil.map(typedDictType.fields(context).values(), PyTypedDictType.FieldTypeAndTotality::getType));
            types.add(extraItemsType);
            return PyUnionType.union(types);
        }

        List<PyType> types = new ArrayList<>();
        for (String key : keys) {
            PyTypedDictType.FieldTypeAndTotality field = typedDictType.fields(context).get(key);
            if (field != null) {
                types.add(field.getType());
                continue;
            }
            PyType extraItemsType = typedDictType.isClosed() ? null : typedDictType.extraItemsType(context);
            types.add(extraItemsType != null ? extraItemsType : PyAnyType.getUnknown());
        }
        return PyUnionType.union(types);
    }

    public static <T> List<T> getIndexExpressionPossibleValues(@Nullable PyExpression indexExpression,
                                                                        TypeEvalContext context,
                                                                        Class<T> indexType) {
        if (indexExpression == null) {
            return List.of();
        }
        T indexExprValue = PyEvaluator.evaluate(indexExpression, indexType);
        if (indexExprValue != null) {
            return List.of(indexExprValue);
        }
        PyType type = context.getType(indexExpression);
        if (type == null) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (PyType subType : PyTypeUtil.toStream(type)) {
            if (!(subType instanceof PyLiteralType literalType)) {
                return List.of();
            }
            T val = extractValueFromLiteral(literalType, indexType);
            if (val == null) {
                return List.of();
            }
            result.add(val);
        }
        return result;
    }

    private static boolean isStringIndex(@Nullable PyExpression indexExpression, TypeEvalContext context) {
        if (indexExpression == null) {
            return false;
        }
        PyType indexType = context.getType(indexExpression);
        if (indexType == null || isUnknown(indexType)) {
            return false;
        }
        PyType strType = PyBuiltinCache.getInstance(indexExpression).getStrType();
        return strType != null && PyTypeChecker.match(strType, indexType, context);
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable T extractValueFromLiteral(PyLiteralType literalType, Class<T> indexType) {
        if ((indexType == Integer.class || indexType == int.class) && literalType.getIntValue() != null) {
            try {
                return (T)Integer.valueOf(literalType.getIntValue().intValueExact());
            }
            catch (ArithmeticException e) {
                // If BigInteger does not fit into int
                return null;
            }
        }
        if (indexType == String.class && literalType.getStringValue() != null) {
            return (T)literalType.getStringValue();
        }
        if ((indexType == Boolean.class || indexType == boolean.class) && literalType.getBoolValue() != null) {
            return (T)literalType.getBoolValue();
        }
        return null;
    }

    @Override
    public PsiReference getReference() {
        return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
    }

    @Override
    public PsiPolyVariantReference getReference(PyResolveContext context) {
        return new PyOperatorReference(this, context);
    }
}
