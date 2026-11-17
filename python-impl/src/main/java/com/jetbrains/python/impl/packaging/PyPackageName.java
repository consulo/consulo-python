// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.packaging;

import java.util.Objects;

public final class PyPackageName {
    private final String myName;

    private PyPackageName(String name) {
        myName = name;
    }

    public String getName() {
        return myName;
    }

    public static PyPackageName from(String name) {
        return new PyPackageName(normalizePackageName(name));
    }

    /**
     * Normalizes a project name according to
     * https://packaging.python.org/en/latest/specifications/name-normalization/#name-format
     * <p>
     * Keeps only lowercase ASCII letters, digits, and hyphens.
     * Replaces everything else with a hyphen, collapses consecutive hyphens, and strips leading/trailing hyphens.
     */
    public static String normalizeProjectName(String name) {
        String result = name
            .toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-{2,}", "-");
        int start = 0;
        int end = result.length();
        while (start < end && result.charAt(start) == '-') {
            start++;
        }
        while (end > start && result.charAt(end - 1) == '-') {
            end--;
        }
        return result.substring(start, end);
    }

    public static String normalizePackageName(String packageName) {
        String name = packageName.trim();
        if (name.startsWith("\"")) {
            name = name.substring(1);
        }
        if (name.endsWith("\"")) {
            name = name.substring(0, name.length() - 1);
        }

        // e.g. __future__
        if (!name.startsWith("_")) {
            name = name.replace('_', '-');
        }

        return name
            .replace('.', '-')
            .toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PyPackageName other && myName.equals(other.myName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(myName);
    }

    @Override
    public String toString() {
        return "PyPackageName(name=" + myName + ")";
    }
}
