// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.application.util.registry.Registry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PyNumericTowerUtil {
    private PyNumericTowerUtil() {
    }

    private static final List<String> ALLOWED_CLASSES = List.of(PyNames.FQN.FLOAT, PyNames.FQN.COMPLEX);

    public static boolean isEnabled() {
        return Registry.is("python.numeric.tower.type", true);
    }

    public static @Nullable PyType enrich(@Nullable PyType type) {
        PyAnyType.validate(type);
        if (!isEnabled()) {
            return type;
        }
        if (type instanceof PyCompositeType compositeType) {
            List<@Nullable PyType> mappedMembers = new ArrayList<>();
            for (PyType typeArg : compositeType.getMembers()) {
                mappedMembers.add(expand(typeArg));
            }
            if (type instanceof PyUnionType) {
                return PyUnionType.unionOrUnknown(mappedMembers);
            }
            else if (type instanceof PyIntersectionType) {
                return PyIntersectionType.intersectionOrTop(mappedMembers);
            }
            else if (type instanceof PyUnsafeUnionType) {
                return PyUnsafeUnionType.unsafeUnion(mappedMembers);
            }
            else {
                throw new IllegalStateException("Unknown type: " + type.getName());
            }
        }
        return expand(type);
    }

    private static @Nullable PyType expand(@Nullable PyType clsType) {
        if (!(clsType instanceof PyClassType classType) || !ALLOWED_CLASSES.contains(classType.getClassQName())) {
            return clsType;
        }
        if (classType.isDefinition()) {
            return clsType;
        }

        PyClass source = classType.getPyClass();
        String classQName = classType.getClassQName();

        PyClassType floatType = PyBuiltinCache.getInstance(source).getFloatType();
        PyClassType intType = PyBuiltinCache.getInstance(source).getIntType();

        List<@Nullable PyType> members;
        if (PyNames.FQN.FLOAT.equals(classQName)) {
            members = Arrays.asList(floatType, intType);
        }
        else if (PyNames.FQN.COMPLEX.equals(classQName)) {
            members = Arrays.asList(PyBuiltinCache.getInstance(source).getComplexType(), floatType, intType);
        }
        else {
            throw new IllegalStateException("Not a numeric tower type: " + classQName);
        }
        return PyUnionType.unionOrUnknown(members);
    }
}
