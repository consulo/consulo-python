// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;

public interface PyNamedTupleStub extends CustomTargetExpressionStub {

    /**
     * @return namedtuple's name.
     */
    String getName();

    /**
     * @return fields' names and their types.
     * Iteration order repeats the declaration order.
     */
    LinkedHashMap<String, FieldTypeAndHasDefault> getFields();

    record FieldTypeAndHasDefault(@Nullable String type, boolean hasDefault) {
    }
}
