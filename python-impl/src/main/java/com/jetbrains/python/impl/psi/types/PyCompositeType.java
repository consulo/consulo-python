// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

// TODO Make it sealed once everything is converted to Kotlin

/**
 * A composite type consists of several alternatives.
 * There are three known kinds of such types: unions, "unsafe" unions, and intersections.
 *
 * @see PyUnionType
 * @see PyIntersectionType
 * @see PyUnsafeUnionType
 */
public interface PyCompositeType extends PyType {
    Collection<@Nullable PyType> getMembers();

    @Override
    default <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyCompositeType(this);
        }
        return visitor.visitPyType(this);
    }
}
