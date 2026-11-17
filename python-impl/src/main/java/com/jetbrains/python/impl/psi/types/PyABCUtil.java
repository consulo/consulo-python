// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;

public final class PyABCUtil {
    private PyABCUtil() {
    }

    public static boolean isSubclass(PyClass subClass, PyClass superClass, @Nullable TypeEvalContext context) {
        String superName = superClass.getName();
        if (superName != null) {
            return isSubclass(subClass, superName, true, context);
        }
        return false;
    }

    public static boolean isSubclass(PyClass subClass, String superClassName, @Nullable TypeEvalContext context) {
        return isSubclass(subClass, superClassName, true, context);
    }

    public static boolean isSubclass(PyClass subClass, String superClassName, boolean inherited, @Nullable TypeEvalContext context) {
        if (PyNames.CALLABLE.equals(superClassName)) {
            return hasMethod(subClass, PyNames.CALL, inherited, context);
        }
        if (PyNames.HASHABLE.equals(superClassName)) {
            return hasMethod(subClass, PyNames.HASH, inherited, context);
        }
        boolean hasIter = hasMethod(subClass, PyNames.ITER, inherited, context);
        boolean hasGetItem = hasMethod(subClass, PyNames.GETITEM, inherited, context);
        if (PyNames.ITERABLE.equals(superClassName)) {
            return hasIter || hasGetItem;
        }
        if (PyNames.ITERATOR.equals(superClassName)) {
            return (hasIter && (hasMethod(subClass, PyNames.NEXT, inherited, context) || hasMethod(
                subClass,
                PyNames.DUNDER_NEXT, inherited, context
            ))) ||
                hasGetItem;
        }
        boolean isSized = hasMethod(subClass, PyNames.LEN, inherited, context);
        if (PyNames.SIZED.equals(superClassName)) {
            return isSized;
        }
        boolean isContainer = hasMethod(subClass, PyNames.CONTAINS, inherited, context);
        if (PyNames.CONTAINER.equals(superClassName)) {
            return isContainer;
        }
        if (PyNames.SEQUENCE.equals(superClassName)) {
            return isSized && hasIter && isContainer && hasGetItem;
        }
        if (PyNames.MAPPING.equals(superClassName)) {
            return isSized && hasIter && isContainer && hasGetItem && hasMethod(subClass, PyNames.KEYS, inherited, context);
        }
        if (PyNames.MUTABLE_MAPPING.equals(superClassName)) {
            boolean hasSetItem = hasMethod(subClass, PyNames.SETITEM, inherited, context);
            boolean hasUpdate = hasMethod(subClass, PyNames.UPDATE, inherited, context);
            return isSized && hasIter && isContainer && hasGetItem && hasSetItem && hasUpdate;
        }
        if (PyNames.ABC_SET.equals(superClassName)) {
            return isSized && hasIter && isContainer;
        }
        if (PyNames.ABC_MUTABLE_SET.equals(superClassName)) {
            return isSized && hasIter && isContainer &&
                hasMethod(subClass, "discard", inherited, context) &&
                hasMethod(subClass, "add", inherited, context);
        }
        if (PyNames.ABC_COMPLEX.equals(superClassName)) {
            return hasMethod(subClass, PyNames.COMPLEX, inherited, context);
        }
        if (PyNames.ABC_REAL.equals(superClassName)) {
            return hasMethod(subClass, PyNames.FLOAT, inherited, context);
        }
        if (PyNames.ABC_INTEGRAL.equals(superClassName)) {
            return hasMethod(subClass, PyNames.INT, inherited, context);
        }
        if (PyNames.ABC_NUMBER.equals(superClassName) && "Decimal".equals(subClass.getName())) {
            return true;
        }
        if (PyNames.ASYNC_ITERABLE.equals(superClassName)) {
            return hasMethod(subClass, PyNames.AITER, inherited, context);
        }
        if (PyNames.AWAITABLE.equals(superClassName)) {
            return hasMethod(subClass, PyNames.DUNDER_AWAIT, inherited, context);
        }
        if (PyNames.ABSTRACT_CONTEXT_MANAGER.equals(superClassName)) {
            return hasMethod(subClass, PyNames.ENTER, inherited, context) && hasMethod(subClass, PyNames.EXIT, inherited, context);
        }
        if (PyNames.ABSTRACT_ASYNC_CONTEXT_MANAGER.equals(superClassName)) {
            return hasMethod(subClass, PyNames.AENTER, inherited, context) && hasMethod(subClass, PyNames.AEXIT, inherited, context);
        }
        return false;
    }

    public static boolean isSubtype(PyType type, String superClassName, TypeEvalContext context) {
        if (type instanceof PyStructuralType) {
            // TODO: Convert abc types to structural types and check them properly
            return true;
        }
        if (type instanceof PyClassType classType) {
            PyClass pyClass = classType.getPyClass();
            if (classType.isDefinition()) {
                PyClassLikeType metaClassType = classType.getMetaClassType(context, true);
                if (metaClassType instanceof PyClassType metaClass) {
                    return isSubclass(metaClass.getPyClass(), superClassName, true, context);
                }
            }
            else {
                return isSubclass(pyClass, superClassName, true, context);
            }
        }
        if (type instanceof PyUnionType) {
            if (!PyUnionType.isStrictSemanticsEnabled()) {
                return PyTypeUtil.toStream(type).filter(it -> !isAnyOrUnknown(it))
                    .anyMatch(it -> isSubtype(Objects.requireNonNull(it), superClassName, context));
            }
            else {
                return PyTypeUtil.toStream(type).filter(it -> !isAnyOrUnknown(it))
                    .allMatch(it -> isSubtype(Objects.requireNonNull(it), superClassName, context));
            }
        }
        else if (type instanceof PyUnsafeUnionType) {
            return PyTypeUtil.toStream(type).filter(it -> !isAnyOrUnknown(it))
                .anyMatch(it -> isSubtype(Objects.requireNonNull(it), superClassName, context));
        }
        else if (type instanceof PyIntersectionType) {
            return PyTypeUtil.toStream(type).filter(it -> !isAnyOrUnknown(it))
                .anyMatch(it -> isSubtype(Objects.requireNonNull(it), superClassName, context));
        }
        else {
            return false;
        }
    }

    private static boolean hasMethod(PyClass cls, String name, boolean inherited, @Nullable TypeEvalContext context) {
        return cls.findMethodByName(name, inherited, context) != null || cls.findClassAttribute(name, inherited, context) != null;
    }
}
