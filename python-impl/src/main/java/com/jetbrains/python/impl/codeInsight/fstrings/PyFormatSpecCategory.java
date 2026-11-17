// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.fstrings;

import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.function.Supplier;

/**
 * The kind of a format spec option, used to group the catalog.
 */
// Declared in PyFormatSpecOption.kt in JetBrains sources
public enum PyFormatSpecCategory {
    ALIGNMENT(PyLocalize::fstringFormatSpecDocCategoryAlignment),
    SIGN(PyLocalize::fstringFormatSpecDocCategorySign),
    FLAG(PyLocalize::fstringFormatSpecDocCategoryFlag),
    PRECISION(PyLocalize::fstringFormatSpecDocCategoryPrecision),
    GROUPING(PyLocalize::fstringFormatSpecDocCategoryGrouping),
    TYPE(PyLocalize::fstringFormatSpecDocCategoryType),
    DATETIME(PyLocalize::fstringFormatSpecDocCategoryDatetime),
    ;

    private final Supplier<LocalizeValue> myLabelSupplier;

    PyFormatSpecCategory(Supplier<LocalizeValue> labelSupplier) {
        myLabelSupplier = labelSupplier;
    }

    /**
     * The human-readable name of the category.
     */
    public LocalizeValue getLabel() {
        return myLabelSupplier.get();
    }
}
