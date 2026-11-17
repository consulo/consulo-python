// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.protocol.ColHeader;
import com.jetbrains.python.console.protocol.DebugValue;
import com.jetbrains.python.console.protocol.GetArrayResponse;
import com.jetbrains.python.console.protocol.RowHeader;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.ArrayChunkBuilder;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.values.DataFrameDebugValue;
import com.jetbrains.python.debugger.values.DataFrameDebugValueUtilKt;
import consulo.execution.debug.frame.XValueChildrenList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PydevConsoleCommunicationUtil {
    private PydevConsoleCommunicationUtil() {
    }

    static XValueChildrenList parseVars(List<DebugValue> vars, @Nullable PyDebugValue parent, PyFrameAccessor frameAccessor) {
        XValueChildrenList list = new XValueChildrenList(vars.size());
        for (DebugValue debugValue : vars) {
            PyDebugValue pyDebugValue = createPyDebugValue(debugValue, frameAccessor);
            if (parent != null) {
                pyDebugValue.setParent(parent);
            }
            list.add(pyDebugValue.getVisibleName(), pyDebugValue);
        }
        return list;
    }

    static PyDebugValue createPyDebugValue(DebugValue value, PyFrameAccessor frameAccessor) {
        if ("DataFrame".equals(value.getType())) {
            DataFrameDebugValue.@Nullable InformationColumns columns;
            try {
                columns = value.getValue() != null ? DataFrameDebugValueUtilKt.getInformationColumns(value.getValue()) : null;
            }
            catch (Exception ignored) {
                columns = null;
            }

            DataFrameDebugValue dataFrameDebugValue = new DataFrameDebugValue(
                value.getName(), value.getType(), value.getQualifier(), value.getValue() != null ? value.getValue() : "",
                value.isIsContainer(), value.getShape(), value.isIsReturnedValue(), value.isIsIPythonHidden(),
                value.isIsErrorOnEval(),
                value.getTypeRendererId(), frameAccessor
            );
            if (columns != null) {
                dataFrameDebugValue.setColumns(columns);
            }
            return dataFrameDebugValue;
        }
        else {
            return new PyDebugValue(
                value.getName(), value.getType(), value.getQualifier(), value.getValue() != null ? value.getValue() : "",
                value.isIsContainer(), value.getShape(), value.getArrayElementType(), value.isIsReturnedValue(), value.isIsIPythonHidden(),
                value.isIsErrorOnEval(), value.getTypeRendererId(), frameAccessor
            );
        }
    }

    static ArrayChunk createArrayChunk(GetArrayResponse response, PyFrameAccessor frameAccessor) {
        ArrayChunkBuilder result = new ArrayChunkBuilder();

        // `parseArrayValues()`

        result.setSlicePresentation(response.getSlice());
        result.setRows(response.getRows());
        result.setColumns(response.getCols());
        result.setFormat(response.getFormat());
        result.setType(response.getType());
        result.setMax(response.getMax());
        result.setMin(response.getMin());
        result.setValue(new PyDebugValue(response.getSlice(), null, null, null, false, null, false, false, false, null, frameAccessor));

        // `parseArrayHeaderData()`

        List<String> rowHeaders = new ArrayList<>();
        List<ArrayChunk.ColHeader> colHeaders = new ArrayList<>();

        if (response.getHeaders() != null && response.getHeaders().getColHeaders() != null) {
            for (ColHeader colHeader : response.getHeaders().getColHeaders()) {
                colHeaders.add(new ArrayChunk.ColHeader(
                    colHeader.getLabel(),
                    colHeader.getType(),
                    colHeader.getFormat(),
                    colHeader.getMax(),
                    colHeader.getMin()
                ));
            }
        }

        if (response.getHeaders() != null && response.getHeaders().getRowHeaders() != null) {
            for (RowHeader rowHeader : response.getHeaders().getRowHeaders()) {
                rowHeaders.add(rowHeader.getLabel());
            }
        }

        result.setColHeaders(colHeaders);
        result.setRowLabels(rowHeaders);

        // `parseArrayValues()`

        List<List<String>> responseData = response.getData().getData();

        Object[][] data = new Object[responseData.size()][];
        for (int rowIndex = 0; rowIndex < responseData.size(); rowIndex++) {
            List<String> responseRowArray = responseData.get(rowIndex);
            Object[] rowValues = new Object[responseRowArray.size()];
            for (int colIndex = 0; colIndex < responseRowArray.size(); colIndex++) {
                rowValues[colIndex] = responseRowArray.get(colIndex);
            }
            data[rowIndex] = rowValues;
        }

        result.setData(data);

        return result.createArrayChunk();
    }
}
