// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.PyClass;
import org.jspecify.annotations.Nullable;

/**
 * Represents a sentinel object type (e.g. {@code SENTINEL = object()}).
 * It behaves exactly like an instance of {@code object}, but has a specific name and strict equality.
 */
public class PySentinelType extends PyClassTypeImpl {
    private final String sentinelName;
    private final String qName;

    public PySentinelType(String sentinelName, String qName, PyClass objectClass) {
        super(objectClass, false);
        this.sentinelName = sentinelName;
        this.qName = qName;
    }

    public String getSentinelName() {
        return sentinelName;
    }

    public String getQName() {
        return qName;
    }

    @Override
    public String getName() {
        return sentinelName;
    }

    @Override
    public String toString() {
        return "PySentinelType: " + sentinelName;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!super.equals(other)) {
            return false;
        }
        PySentinelType otherType = (PySentinelType)other;
        return qName.equals(otherType.qName);
    }

    @Override
    public int hashCode() {
        return 31 * getPyClass().hashCode() + qName.hashCode();
    }
}
