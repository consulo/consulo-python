// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class PyOverloadTypeKt {
    private PyOverloadTypeKt() {
    }

    public static @Nullable PyType map(PyOverloadType overloadType, Function<PyCallableType, @Nullable PyCallableType> mapper) {
        List<PyCallableType> newItems = new ArrayList<>();
        for (PyCallableType item : overloadType.getItems()) {
            PyCallableType mapped = mapper.apply(item);
            if (mapped != null) {
                newItems.add(mapped);
            }
        }
        if (newItems.isEmpty()) {
            return null;
        }
        if (newItems.size() == 1) {
            return newItems.get(0);
        }
        Ref<@Nullable PyType> impl = overloadType.getImpl();
        Ref<@Nullable PyType> newImpl = impl;
        if (impl != null) {
            PyType impl_ = impl.get();
            if (impl_ instanceof PyCallableType callableImpl) {
                newImpl = Ref.create(mapper.apply(callableImpl));
            }
        }
        return new PyOverloadType(newItems, newImpl);
    }
}
