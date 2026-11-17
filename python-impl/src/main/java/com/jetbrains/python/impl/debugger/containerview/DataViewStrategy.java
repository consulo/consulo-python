// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.impl.debugger.array.ArrayViewStrategy;
import com.jetbrains.python.impl.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.impl.debugger.dataframe.DataFrameViewStrategy;
import com.jetbrains.python.impl.debugger.dataframe.SeriesViewStrategy;
import org.jspecify.annotations.Nullable;

import javax.swing.RowSorter;
import java.util.Set;

public abstract class DataViewStrategy {
    private static class StrategyHolder {
        private static final Set<DataViewStrategy> STRATEGIES = Set.of(
            ArrayViewStrategy.createInstanceForNumpyArray(),
            ArrayViewStrategy.createInstanceForNumpyRecarray(),
            ArrayViewStrategy.createInstanceForEagerTensor(),
            ArrayViewStrategy.createInstanceForResourceVariable(),
            ArrayViewStrategy.createInstanceForSparseTensor(),
            ArrayViewStrategy.createInstanceForTensor(),
            DataFrameViewStrategy.createInstanceForDataFrame(),
            DataFrameViewStrategy.createInstanceForGeoDataFrame(),
            DataFrameViewStrategy.createInstanceForDataset(),
            SeriesViewStrategy.createInstanceForSeries(),
            SeriesViewStrategy.createInstanceForGeoSeries()
        );
    }

    public abstract AsyncArrayTableModel createTableModel(int rowCount,
                                                          int columnCount,
                                                          PyDataViewerCommunityPanel panel,
                                                          PyDebugValue debugValue);

    public abstract ColoredCellRenderer createCellRenderer(double minValue, double maxValue, ArrayChunk arrayChunk);

    public abstract boolean isNumeric(@Nullable String dtypeKind);

    public abstract String sortModifier(String varName, RowSorter.SortKey key);

    public abstract String filterModifier(String varName, ColumnFilter filter);

    public @Nullable String getInitExecuteString() {
        return null;
    }

    public abstract String getTypeName();

    public boolean showColumnHeader() {
        return true;
    }

    /**
     * @return null if no strategy for this type
     */
    public static @Nullable DataViewStrategy getStrategy(String type) {
        for (DataViewStrategy strategy : StrategyHolder.STRATEGIES) {
            if (strategy.getTypeName().equals(type)) {
                return strategy;
            }
        }
        return null;
    }
}
