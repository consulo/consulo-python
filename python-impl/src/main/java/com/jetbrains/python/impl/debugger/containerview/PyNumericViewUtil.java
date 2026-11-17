// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import consulo.ui.style.StyleManager;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PyNumericViewUtil {
    private static final Pattern PY_COMPLEX_NUMBER =
        Pattern.compile("([+-]?[.\\d]*(?:[eE][+-]?\\d*)?j?)?([+-]?[.\\d]*(?:[eE][+-]?\\d*)?j)?");

    /**
     * @return double presentation from [0:1] range
     */
    public static double getRangedValue(String value, String type, double min, double max, String complexMax, String complexMin) {
        if ("iuf".contains(type)) {
            return (Double.parseDouble(value) - min) / (max - min);
        }
        else if ("b".equals(type)) {
            return value.equals("True") ? 1 : 0;
        }
        else if ("c".equals(type)) {
            return getComplexRangedValue(value, complexMax, complexMin);
        }
        return 0;
    }

    public static Color rangedValueToColor(double rangedValue) {
        // JBColor.isBright() -> !StyleManager.get().getCurrentStyle().isDark() in Consulo
        if (!StyleManager.get().getCurrentStyle().isDark()) {
            return Color.getHSBColor(240 / 360f, (float) rangedValue * 0.7f, 1f);
        }
        else {
            return Color.getHSBColor(240 / 360f, 220 / 360f, (float) rangedValue * 0.7f);
        }
    }

    /**
     * type complex128 in numpy is compared by next rule:
     * A + Bj > C +Dj if A > C or A == C and B > D
     */
    private static double getComplexRangedValue(String value, String complexMax, String complexMin) {
        Pair<Double, Double> med = parsePyComplex(value);
        Pair<Double, Double> max = parsePyComplex(complexMax);
        Pair<Double, Double> min = parsePyComplex(complexMin);
        if (med == null || min == null || max == null) {
            return 0;
        }
        double range = (med.first - min.first) / (max.first - min.first);
        if (max.first.equals(min.first)) {
            range = (med.second - min.second) / (max.second - min.second);
        }
        return range;
    }

    private static @Nullable Pair<Double, Double> parsePyComplex(String pyComplexValue) {
        if (pyComplexValue.equals("nan")) {
            return null;
        }
        String value = pyComplexValue;
        if (value.startsWith("(") && value.endsWith(")")) {
            value = value.substring(1, value.length() - 1);
        }
        Matcher matcher = PY_COMPLEX_NUMBER.matcher(value);
        if (matcher.matches()) {
            String real = matcher.group(1);
            String imag = matcher.group(2);
            if (imag == null && real.contains("j")) {
                return new Pair<>(Double.valueOf(0.0), Double.parseDouble(real.substring(0, real.length() - 1)));
            }
            else if (imag != null) {
                return new Pair<>(Double.parseDouble(real), Double.parseDouble(imag.substring(0, imag.length() - 1)));
            }
        }
        else {
            throw new NumberFormatException("Not a valid python complex value: " + value);
        }
        return null;
    }
}
