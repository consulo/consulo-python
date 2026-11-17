// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PEP 800: Disjoint bases in the type system.
 * <p>
 * Two classes with different, unrelated disjoint bases cannot have a common child class.
 */
public final class PyDisjointBaseUtil {
    private PyDisjointBaseUtil() {
    }

    public static boolean areDisjoint(PyClassType type1, PyClassType type2, TypeEvalContext context) {
        PyClass class1 = type1.getPyClass();
        PyClass class2 = type2.getPyClass();

        PyClass disjointBase1 = findDisjointBase(class1, context);
        if (disjointBase1 == null) {
            return false;
        }
        PyClass disjointBase2 = findDisjointBase(class2, context);
        if (disjointBase2 == null) {
            return false;
        }

        if (disjointBase1.equals(disjointBase2)) {
            return false;
        }
        if (disjointBase1.isSubclass(disjointBase2, context) || disjointBase2.isSubclass(disjointBase1, context)) {
            return false;
        }

        return true;
    }

    private static @Nullable PyClass findDisjointBase(PyClass pyClass, TypeEvalContext context) {
        if (isDisjointBase(pyClass, context)) {
            return pyClass;
        }

        Set<PyClass> candidates = new LinkedHashSet<>();
        for (PyClass ancestor : pyClass.getAncestorClasses(context)) {
            if (isDisjointBase(ancestor, context)) {
                candidates.add(ancestor);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // Per PEP 800: if multiple disjoint base candidates exist, one must be a subclass of all others.
        for (PyClass candidate : candidates) {
            boolean all = true;
            for (PyClass it : candidates) {
                if (!candidate.isSubclass(it, context)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Per PEP 800, a class is a disjoint base if:
     * - It has @disjoint_base decorator
     * - It has non-empty `__slots__` (explicit or synthesized via @dataclass(slots=True) or dataclass_transform)
     */
    public static boolean isDisjointBase(PyClass pyClass, TypeEvalContext context) {
        return hasDisjointBaseDecorator(pyClass, context) || hasNonEmptySlots(pyClass, context);
    }

    private static boolean hasDisjointBaseDecorator(PyClass pyClass, TypeEvalContext context) {
        return PyKnownDecoratorUtil.knownDecoratorsOf(pyClass, context)
            .containsAny(PyKnownDecorator.DISJOINT_BASE, PyKnownDecorator.DISJOINT_BASE_EXT);
    }

    private static boolean hasNonEmptySlots(PyClass pyClass, TypeEvalContext context) {
        List<String> ownSlots = pyClass.getOwnSlots();
        if (ownSlots != null && !ownSlots.isEmpty()) {
            return true;
        }

        List<String> synthesized = PyClassImpl.getOwnSlotsSynthesized(pyClass, context);
        return synthesized != null && !synthesized.isEmpty();
    }
}
