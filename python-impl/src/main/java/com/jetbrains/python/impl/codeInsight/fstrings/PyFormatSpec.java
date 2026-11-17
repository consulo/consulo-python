// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.fstrings;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The single parser of the Python format mini-language.
 * <p>
 * Every consumer reads a spec through {@link PyFormatSpec#parse}, so the annotator, the completion contributor
 * and the documentation providers agree on what a character means. Each consumer still applies its own
 * policy on top. The annotator highlights whatever the syntax allows, and completion offers only what the
 * value type accepts.
 * <p>
 * A parsed format spec.
 * <p>
 * The scan is lenient on purpose. It classifies each character it recognizes wherever it appears, instead
 * of rejecting a spec whose components are out of order, because a partly typed or an invalid spec still
 * needs highlighting and documentation.
 */
public final class PyFormatSpec {
    /**
     * A datetime directive is {@code %} and a letter (e.g. {@code %Y}) or {@code %%}. A lone or trailing {@code %} is the percentage type.
     */
    private static final Pattern DATETIME_DIRECTIVE = Pattern.compile("%[A-Za-z%]");

    public static final String ALIGN_CHARS = "<>=^";
    public static final String SIGN_CHARS = "+- ";
    public static final String GROUPING_CHARS = ",_";
    public static final String PRESENTATION_TYPE_CHARS = "bcdeEfFgGnosxX%";

    private final String myText;
    private final List<PyFormatSpecComponent> myComponents;

    private PyFormatSpec(String text, List<PyFormatSpecComponent> components) {
        myText = text;
        myComponents = components;
    }

    public String getText() {
        return myText;
    }

    public List<PyFormatSpecComponent> getComponents() {
        return myComponents;
    }

    /**
     * The first component of {@code kind}, or {@code null} when the spec has none.
     */
    public @Nullable PyFormatSpecComponent first(PyFormatSpecComponentKind kind) {
        for (PyFormatSpecComponent component : myComponents) {
            if (component.getKind() == kind) {
                return component;
            }
        }
        return null;
    }

    public boolean has(PyFormatSpecComponentKind kind) {
        for (PyFormatSpecComponent component : myComponents) {
            if (component.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    public boolean isDatetime() {
        return has(PyFormatSpecComponentKind.DATETIME_DIRECTIVE);
    }

    public List<String> getDatetimeDirectives() {
        List<String> result = new ArrayList<>();
        for (PyFormatSpecComponent component : myComponents) {
            if (component.getKind() == PyFormatSpecComponentKind.DATETIME_DIRECTIVE) {
                result.add(component.getText());
            }
        }
        return result;
    }

    /**
     * True when the spec already carries a presentation type, so nothing more can follow it.
     */
    public boolean getHasPresentationType() {
        return has(PyFormatSpecComponentKind.TYPE);
    }

    /**
     * The padding character of a {@code [fill]align} pair.
     */
    public @Nullable Character getFill() {
        return charOf(PyFormatSpecComponentKind.FILL);
    }

    public @Nullable Character getAlign() {
        return charOf(PyFormatSpecComponentKind.ALIGN);
    }

    public @Nullable Character getSign() {
        return charOf(PyFormatSpecComponentKind.SIGN);
    }

    public @Nullable Character getGrouping() {
        return charOf(PyFormatSpecComponentKind.GROUPING);
    }

    public @Nullable Character getType() {
        return charOf(PyFormatSpecComponentKind.TYPE);
    }

    public boolean getHasCoerceNegativeZero() {
        return has(PyFormatSpecComponentKind.COERCE_NEGATIVE_ZERO);
    }

    public boolean getHasAlternateForm() {
        return has(PyFormatSpecComponentKind.ALTERNATE_FORM);
    }

    public boolean getHasZeroPadding() {
        return has(PyFormatSpecComponentKind.ZERO_PADDING);
    }

    /**
     * The digits of the minimum field width.
     */
    public @Nullable String getWidth() {
        PyFormatSpecComponent component = first(PyFormatSpecComponentKind.WIDTH);
        return component != null ? component.getText() : null;
    }

    /**
     * The digits after the {@code .}, an empty string when the spec carries a bare {@code .}, or {@code null} when it states
     * no precision at all.
     */
    public @Nullable String getPrecision() {
        if (has(PyFormatSpecComponentKind.PRECISION_MARK)) {
            PyFormatSpecComponent component = first(PyFormatSpecComponentKind.PRECISION);
            return component != null ? component.getText() : "";
        }
        return null;
    }

    /**
     * True when the scan recognized nothing at all, so there is nothing to describe.
     */
    public boolean isEmpty() {
        return myComponents.isEmpty();
    }

    private @Nullable Character charOf(PyFormatSpecComponentKind kind) {
        PyFormatSpecComponent component = first(kind);
        if (component == null || component.getText().isEmpty()) {
            return null;
        }
        return component.getText().charAt(0);
    }

    /**
     * True when {@code text} reads as a datetime spec rather than a standard one.
     */
    public static boolean looksLikeDatetime(String text) {
        return DATETIME_DIRECTIVE.matcher(text).find();
    }

    public static PyFormatSpec parse(String text) {
        return parse(text, looksLikeDatetime(text));
    }

    /**
     * Parses {@code text}, the spec that follows the colon.
     * <p>
     * Pass {@code datetime = true} to read the text as a strftime string. A caller that knows the value type
     * decides from the type, and one that does not uses {@link #looksLikeDatetime}.
     */
    public static PyFormatSpec parse(String text, boolean datetime) {
        return new PyFormatSpec(text, datetime ? parseDatetime(text) : parseStandard(text));
    }

    private static List<PyFormatSpecComponent> parseDatetime(String text) {
        List<PyFormatSpecComponent> components = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '%' && i + 1 < text.length()) {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.DATETIME_DIRECTIVE, text.substring(i, i + 2), i));
                i += 2;
            }
            else {
                i++;
            }
        }
        return components;
    }

    private static List<PyFormatSpecComponent> parseStandard(String text) {
        List<PyFormatSpecComponent> components = new ArrayList<>();

        // A fill character is only a fill when an alignment operator follows it, so a fill that happens to be
        // a presentation-type letter (the 'f' in "f>5") is not read as a type.
        int i = 0;
        if (text.length() > 1 && ALIGN_CHARS.indexOf(text.charAt(1)) >= 0) {
            components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.FILL, text.substring(0, 1), 0));
            components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.ALIGN, text.substring(1, 2), 1));
            i = 2;
        }

        boolean seenZeroPadding = false;
        boolean seenDigit = false;
        boolean seenPrecisionMark = false;

        while (i < text.length()) {
            char c = text.charAt(i);
            if (ALIGN_CHARS.indexOf(c) >= 0) {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.ALIGN, String.valueOf(c), i));
                i++;
            }
            else if (SIGN_CHARS.indexOf(c) >= 0) {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.SIGN, String.valueOf(c), i));
                i++;
            }
            else if (c == 'z') {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.COERCE_NEGATIVE_ZERO, String.valueOf(c), i));
                i++;
            }
            else if (c == '#') {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.ALTERNATE_FORM, String.valueOf(c), i));
                i++;
            }
            // A leading '0' is the sign-aware zero-padding flag only when a width follows it.
            else if (c == '0' && !seenZeroPadding && !seenDigit && i + 1 < text.length() && Character.isDigit(text.charAt(i + 1))) {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.ZERO_PADDING, String.valueOf(c), i));
                seenZeroPadding = true;
                i++;
            }
            else if (Character.isDigit(c)) {
                int end = digitRunEnd(text, i);
                PyFormatSpecComponentKind kind = seenPrecisionMark ? PyFormatSpecComponentKind.PRECISION : PyFormatSpecComponentKind.WIDTH;
                components.add(new PyFormatSpecComponent(kind, text.substring(i, end), i));
                seenDigit = true;
                i = end;
            }
            else if (GROUPING_CHARS.indexOf(c) >= 0) {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.GROUPING, String.valueOf(c), i));
                i++;
            }
            else if (c == '.') {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.PRECISION_MARK, String.valueOf(c), i));
                seenPrecisionMark = true;
                i++;
            }
            // A presentation type ends the spec, so it only counts at the end of the text.
            else if (PRESENTATION_TYPE_CHARS.indexOf(c) >= 0 && isAtSpecEnd(text, i)) {
                components.add(new PyFormatSpecComponent(PyFormatSpecComponentKind.TYPE, String.valueOf(c), i));
                i++;
            }
            else {
                i++;
            }
        }

        return components;
    }

    private static int digitRunEnd(String text, int start) {
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean isAtSpecEnd(String text, int index) {
        return index == text.length() - 1 || " \t}".indexOf(text.charAt(index + 1)) >= 0;
    }
}
