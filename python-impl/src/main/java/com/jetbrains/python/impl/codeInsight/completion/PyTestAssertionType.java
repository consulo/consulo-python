// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import org.jspecify.annotations.Nullable;

// Declared in PyCodeInsightTestCaseOnlyCompletionContributor.kt in JetBrains sources
public enum PyTestAssertionType {
    TYPE,
    EXPECTED_TYPE,
    IS_BUILTIN,
    EXPECTED_VARIANCE,
    INFERRED_VARIANCE,
    ISSUES,

    /**
     * Puts the caret at the marked position, see `PyCodeInsightTestCase.configureWithCaret`. It asserts nothing.
     */
    CARET,
    ;

    /**
     * Modifier of a `<SEVERITY>` assertion, switching the check to the highlight's tooltip.
     * <p>
     * Example: ` WARNING TOOLTIP incompatible with protocol \n Attribute 'a' is missing`
     * <p>
     * The expectation of a TOOLTIP has a contains-semantic, i.e., the text only needs to be contained in the actual tooltip.
     * In case a tooltip is a multiline string, use `\n` as a line separator. Also, the contains-semantic applies to every
     * expected line given in a multiline string.
     * <p>
     * In case the expectation of a TOOLTIP contains closing html tags (`</`, or `/>`) it will be compared against the actual
     * tooltip. Otherwise, the actual tooltip will be cleared of all HTML tags.
     */
    public static final String TOOLTIP_KEYWORD = "TOOLTIP";

    public static @Nullable PyTestAssertionType fromValue(String value) {
        for (PyTestAssertionType it : values()) {
            if (it.name().equalsIgnoreCase(value)) {
                return it;
            }
        }
        return null;
    }
}
