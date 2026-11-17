// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.dataframe;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.impl.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.impl.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.impl.debugger.containerview.ColumnFilter;
import com.jetbrains.python.impl.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.impl.debugger.containerview.PyDataViewerCommunityPanel;
import org.jspecify.annotations.Nullable;

import javax.swing.RowSorter;
import javax.swing.SortOrder;

public class DataFrameViewStrategy extends DataViewStrategy {

    private final String myTypeName;

    public static DataFrameViewStrategy createInstanceForDataFrame() {
        return new DataFrameViewStrategy("DataFrame");
    }

    public static DataFrameViewStrategy createInstanceForGeoDataFrame() {
        return new DataFrameViewStrategy("GeoDataFrame");
    }

    public static DataFrameViewStrategy createInstanceForDataset() {
        return new DataFrameViewStrategy("Dataset");
    }

    protected DataFrameViewStrategy(String typeName) {
        this.myTypeName = typeName;
    }

    @Override
    public AsyncArrayTableModel createTableModel(int rowCount,
                                                 int columnCount,
                                                 PyDataViewerCommunityPanel dataProvider,
                                                 PyDebugValue debugValue) {
        return new DataFrameTableModel(rowCount, columnCount, dataProvider, debugValue, this);
    }

    @Override
    public ColoredCellRenderer createCellRenderer(double minValue, double maxValue, ArrayChunk arrayChunk) {
        return new DataFrameTableCellRenderer();
    }

    @Override
    public boolean isNumeric(@Nullable String dtypeKind) {
        return true;
    }

    @Override
    public String sortModifier(String varName, RowSorter.SortKey key) {
        return String.format("%s.sort_values(by=%s.columns[%d]%s)", varName, varName, key.getColumn(),
            key.getSortOrder() == SortOrder.ASCENDING ? "" : ", ascending=False");
    }

    @Override
    public String filterModifier(String varName, ColumnFilter filter) {
        if (filter.isSubstring()) {
            return String.format("%1$s[%1$s.iloc[:, %2$d].apply(str).str.contains('%3$s', regex=%4$s)]",
                varName, filter.getColumn(), filter.getFilter(), filter.isRegex() ? "True" : "False");
        }

        return String.format("%1$s[%1$s.iloc[:, %2$d].apply(lambda %4$s: bool(%3$s))]",
            varName, filter.getColumn(), filter.getFilter(), ColumnFilter.VAR_ALIAS);
    }

    @Override
    public String getTypeName() {
        return myTypeName;
    }
}
