// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import consulo.language.editor.annotation.HighlightSeverity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.GUIDE_BAR;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.GUIDE_BAR_ASCII;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_CORNER;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_LEFT;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_SPAN;

// Declared in PyCodeInsightTestCaseOnlyCompletionContributor.kt in JetBrains sources
public final class PyTestAssertionParserUtils {
    private PyTestAssertionParserUtils() {
    }

    public static final List<String> DEFAULT_SEVERITY_NAMES = createDefaultSeverityNames();

    private static List<String> createDefaultSeverityNames() {
        List<String> result = new ArrayList<>();
        for (HighlightSeverity severity : HighlightSeverity.DEFAULT_SEVERITIES) {
            result.add(severity.getName().replace(" ", "-"));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<String> getDefaultSeverityNames() {
        return DEFAULT_SEVERITY_NAMES;
    }

    public static int skipWhitespace(String text, int startIndex) {
        int i = startIndex;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    public static int skipWhitespaceAndGuides(String text) {
        int i = 0;
        while (i < text.length() && (Character.isWhitespace(text.charAt(i)) || isGuide(text.charAt(i)))) {
            i++;
        }
        return i;
    }

    public static boolean isGuide(char ch) {
        return ch == GUIDE_BAR || ch == GUIDE_BAR_ASCII;
    }

    public static int scanTokenEnd(String text, int startIndex) {
        int i = startIndex;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    public static boolean isAssertionMarker(char ch) {
        return ch == MARKER_CORNER || ch == MARKER_LEFT || ch == MARKER_SPAN || ch == GUIDE_BAR;
    }
}
