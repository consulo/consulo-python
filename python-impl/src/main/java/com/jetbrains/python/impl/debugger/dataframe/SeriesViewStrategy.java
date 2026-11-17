// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.dataframe;

import com.jetbrains.python.impl.debugger.containerview.ColumnFilter;

import javax.swing.RowSorter;
import javax.swing.SortOrder;

public class SeriesViewStrategy extends DataFrameViewStrategy {

    protected SeriesViewStrategy(String typeName) {
        super(typeName);
    }

    public static SeriesViewStrategy createInstanceForSeries() {
        return new SeriesViewStrategy("Series");
    }

    public static SeriesViewStrategy createInstanceForGeoSeries() {
        return new SeriesViewStrategy("GeoSeries");
    }

    @Override
    public String sortModifier(String varName, RowSorter.SortKey key) {
        return String.format("%s.sort_values(%s)", varName,
            key.getSortOrder() == SortOrder.ASCENDING ? "" : "ascending=False");
    }

    @Override
    public String filterModifier(String varName, ColumnFilter filter) {
        if (filter.isSubstring()) {
            return String.format("%1$s[%1$s.apply(str).str.contains('%2$s', regex=%3$s)]",
                varName, filter.getFilter(), filter.isRegex() ? "True" : "False");
        }

        return String.format("%1$s[%1$s.apply(lambda %3$s: bool(%2$s))]", varName, filter.getFilter(), ColumnFilter.VAR_ALIAS);
    }
}
