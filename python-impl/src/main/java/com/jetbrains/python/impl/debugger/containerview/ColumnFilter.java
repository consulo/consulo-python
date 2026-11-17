// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

public class ColumnFilter {
    public enum Mode {
        EXPRESSION,
        SUBSTRING,
        REGEX
    }

    public static final String VAR_ALIAS = "x";

    private final int myColumn;
    private final String myFilter;

    private final Mode myMode;
    private final String myStrFormat;

    public ColumnFilter(int column, String filter, Mode mode, String strFormat) {
        myColumn = column;
        myFilter = filter;
        myMode = mode;
        myStrFormat = strFormat;
    }

    public ColumnFilter(int column, String filter, Mode mode) {
        this(column, filter, mode, "%s");
    }

    public Mode getMode() {
        return myMode;
    }

    public int getColumn() {
        return myColumn;
    }

    public boolean isSubstring() {
        return myMode == Mode.SUBSTRING || myMode == Mode.REGEX;
    }

    public boolean isRegex() {
        return myMode == Mode.REGEX;
    }

    public boolean isExpression() {
        return myMode == Mode.EXPRESSION;
    }

    public String getFilter() {
        return myFilter;
    }

    public String getStrFormat() {
        return myStrFormat;
    }

    public String replaceAlias(String replacement) {
        return myFilter.replace(VAR_ALIAS, replacement);
    }

    public boolean isEmpty() {
        return myFilter.isEmpty();
    }
}
