// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PyNestedClassUtils {
    private static final int MAX_NESTING_DEPTH = 10;

    private PyNestedClassUtils() {
    }

    public static @Nullable PyClass findTopLevelClass(PyClass nested) {
        if (PyUtil.isTopLevel(nested)) {
            return nested;
        }

        PyClass current = nested;
        PyClass parent = PsiTreeUtil.getParentOfType(current, PyClass.class);
        while (parent != null) {
            current = parent;
            parent = PsiTreeUtil.getParentOfType(current, PyClass.class);
        }

        return PyUtil.isTopLevel(current) ? current : null;
    }

    /**
     * Builds a qualified name from a nested class to the outermost class.
     * <p>
     * Example: For {@code Outer.Middle.Inner}, returns "Outer.Middle.Inner"
     *
     * @param nested    Inner class (e.g., Inner)
     * @param outermost Top-level class (e.g., Outer)
     * @return Qualified name like "Outer.Middle.Inner", or null if the hierarchy is invalid
     */
    public static @Nullable String buildQualifiedName(PyClass nested, PyClass outermost) {
        if (!nested.getContainingFile().equals(outermost.getContainingFile())) {
            return null;
        }

        Deque<String> names = new ArrayDeque<>();
        PyClass current = nested;
        int depth = 0;

        while (current != null && depth < MAX_NESTING_DEPTH) {
            String name = current.getName();
            if (name == null || name.isEmpty()) {
                return null;
            }
            names.addFirst(name);
            if (current.equals(outermost)) {
                return String.join(".", names);
            }
            current = PsiTreeUtil.getParentOfType(current, PyClass.class);
            depth++;
        }

        // nested is not a descendant of outermost
        return null;
    }

    public static @Nullable PyClass findNestedByName(PyClass parent, String name) {
        return findNestedByNameInternal(parent, name, MAX_NESTING_DEPTH);
    }

    private static @Nullable PyClass findNestedByNameInternal(PyClass parent, String name, int maxDepth) {
        if (maxDepth <= 0) {
            return null;
        }

        for (PyClass nested : parent.getNestedClasses()) {
            if (name.equals(nested.getName())) {
                return nested;
            }
            PyClass deeper = findNestedByNameInternal(nested, name, maxDepth - 1);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }
}
