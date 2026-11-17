// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.array;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.impl.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.impl.debugger.containerview.ColumnFilter;
import com.jetbrains.python.impl.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.impl.debugger.containerview.PyDataViewerCommunityPanel;
import org.jspecify.annotations.Nullable;

import javax.swing.RowSorter;
import javax.swing.SortOrder;

public class ArrayViewStrategy extends DataViewStrategy {
    private final String myTypeName;

    public static ArrayViewStrategy createInstanceForNumpyArray() {
        return new ArrayViewStrategy("ndarray");
    }

    public static ArrayViewStrategy createInstanceForNumpyRecarray() {
        return new ArrayViewStrategy("recarray");
    }

    public static ArrayViewStrategy createInstanceForEagerTensor() {
        return new ArrayViewStrategy("EagerTensor");
    }

    public static ArrayViewStrategy createInstanceForResourceVariable() {
        return new ArrayViewStrategy("ResourceVariable");
    }

    public static ArrayViewStrategy createInstanceForSparseTensor() {
        return new ArrayViewStrategy("SparseTensor");
    }

    public static ArrayViewStrategy createInstanceForTensor() {
        return new ArrayViewStrategy("Tensor");
    }

    protected ArrayViewStrategy(String typeName) {
        this.myTypeName = typeName;
    }

    @Override
    public AsyncArrayTableModel createTableModel(int rowCount,
                                                 int columnCount,
                                                 PyDataViewerCommunityPanel panel,
                                                 PyDebugValue debugValue) {
        return new AsyncArrayTableModel(rowCount, columnCount, panel, debugValue, this);
    }

    @Override
    public ColoredCellRenderer createCellRenderer(double minValue, double maxValue, ArrayChunk arrayChunk) {
        ArrayTableCellRenderer renderer = new ArrayTableCellRenderer(minValue, maxValue, arrayChunk.getType());
        renderer.fillColorRange(arrayChunk.getMin(), arrayChunk.getMax());
        return renderer;
    }

    @Override
    public boolean isNumeric(@Nullable String dtypeKind) {
        if (dtypeKind != null) {
            return "biufc".contains(dtypeKind.substring(0, 1));
        }
        return false;
    }

    @Override
    public String sortModifier(String varName, RowSorter.SortKey key) {
        return String.format("%s[%s[:,%d].argsort()%s]", varName, varName, key.getColumn(),
            key.getSortOrder() == SortOrder.ASCENDING ? "" : "[::-1]");
    }

    @Override
    public String filterModifier(String varName, ColumnFilter filter) {
        if (filter.isSubstring()) {
            throw new UnsupportedOperationException("Substring search is not supported on numpy arrays");
        }

        return String.format("%1$s[_np_vectorize(lambda %4$s: bool(%2$s))(%1$s[:, %3$d])]",
            varName, filter.getFilter(), filter.getColumn(), ColumnFilter.VAR_ALIAS);
    }

    @Override
    public @Nullable String getInitExecuteString() {
        return "import numpy as _np; _np_vectorize = _np.vectorize";
    }

    @Override
    public String getTypeName() {
        return myTypeName;
    }
}
