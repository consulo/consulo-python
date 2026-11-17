// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.fstrings;

import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * One option of the format mini-language, with its documentation.
 * <p>
 * The descriptions resolve from {@code PyLocalize} on demand, so building the catalog does not read the whole
 * bundle while the class initializes.
 */
public class PyFormatSpecOption {
    private final String mySpec;
    private final PyFormatSpecCategory myCategory;
    private final Supplier<LocalizeValue> myShortDescriptionSupplier;
    private final Supplier<LocalizeValue> myFullDescriptionSupplier;
    /**
     * A canonical one-line example of the option, or {@code null} when it has none.
     */
    private final @Nullable String myExample;

    PyFormatSpecOption(String spec,
                       PyFormatSpecCategory category,
                       Supplier<LocalizeValue> shortDescriptionSupplier,
                       Supplier<LocalizeValue> fullDescriptionSupplier,
                       @Nullable String example) {
        mySpec = spec;
        myCategory = category;
        myShortDescriptionSupplier = shortDescriptionSupplier;
        myFullDescriptionSupplier = fullDescriptionSupplier;
        myExample = example;
    }

    public String getSpec() {
        return mySpec;
    }

    public PyFormatSpecCategory getCategory() {
        return myCategory;
    }

    /**
     * A canonical one-line example of the option, or {@code null} when it has none.
     */
    public @Nullable String getExample() {
        return myExample;
    }

    public LocalizeValue getShortDescription() {
        return myShortDescriptionSupplier.get();
    }

    public LocalizeValue getFullDescription() {
        return myFullDescriptionSupplier.get();
    }

    @Override
    public String toString() {
        return "PyFormatSpecOption(" + mySpec + ", " + myCategory + ")";
    }
}
