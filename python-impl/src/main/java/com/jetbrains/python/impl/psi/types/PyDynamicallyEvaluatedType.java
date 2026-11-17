// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;

public final class PyDynamicallyEvaluatedType extends PyUnionType {
    private PyDynamicallyEvaluatedType(LinkedHashSet<@Nullable PyType> members) {
        super(members);
    }

    public static PyDynamicallyEvaluatedType create(PyType type) {
        LinkedHashSet<PyType> members = new LinkedHashSet<>();
        if (type instanceof PyUnionType unionType) {
            members.addAll(unionType.getMembers());
        }
        else {
            members.add(type);
        }
        members.add(PyAnyType.getUnknown());
        return new PyDynamicallyEvaluatedType(members);
    }

    @Override
    public String getName() {
        PyType res = excludeNull();
        return res != null ? res.getName() : PyNames.ANY_TYPE;
    }
}
