// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.values;

import consulo.util.collection.SmartFMap;
import consulo.execution.debug.frame.XValueNode;
import consulo.localize.LocalizeValue;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.ValuesPolicy;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.jetbrains.python.debugger.values.DataFrameDebugValueUtilKt.getInformationColumns;

public final class DataFrameDebugValue extends PyDebugValue {
    private final ColumnNode treeColumns = new ColumnNode();

    public static final String pyDataFrameType = "DataFrame";

    public DataFrameDebugValue(String name,
                               @Nullable String type,
                               @Nullable String typeQualifier,
                               @Nullable String value,
                               boolean container,
                               @Nullable String shape,
                               boolean isReturnedVal,
                               boolean isIPythonHidden,
                               boolean errorOnEval,
                               @Nullable String typeRendererId,
                               PyFrameAccessor frameAccessor) {
        super(name, type, typeQualifier, value, container, shape, null, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId, null, frameAccessor);
    }

    public DataFrameDebugValue(String name,
                               @Nullable String type,
                               @Nullable String typeQualifier,
                               @Nullable String value,
                               boolean container,
                               @Nullable String shape,
                               boolean isReturnedVal,
                               boolean isIPythonHidden,
                               boolean errorOnEval,
                               @Nullable String typeRendererId,
                               @Nullable PyDebugValue parent,
                               PyFrameAccessor frameAccessor) {
        super(name, type, typeQualifier, value, container, shape, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId, parent,
              frameAccessor);
    }

    public DataFrameDebugValue(PyDebugValue value, String newName) {
        super(value, newName);
    }

    public DataFrameDebugValue(PyDebugValue value) {
        super(value);
    }


    @Override
    public PyDebugCallback<String> createDebugValueCallback() {
        return new PyDebugCallback<>() {
            @Override
            public void ok(String value) {
                myLoadValuePolicy = ValuesPolicy.SYNC;
                myValue = value;
                try {
                    DataFrameDebugValue.InformationColumns columns = getInformationColumns(value);
                    if (columns != null) {
                        setColumns(columns);
                    }
                }
                catch (Exception ignored) {}

                for (XValueNode node : myValueNodes) {
                    if (node != null && !node.isObsolete()) {
                        updateNodeValueAfterLoading(node, value, LocalizeValue.empty(), null);
                    }
                }
            }

            @Override
            public void error(PyDebuggerException exception) {
                LOG.error(exception.getMessage());
            }
        };
    }


    public ColumnNode getTreeColumns() {
        return treeColumns;
    }


    public void setColumns(InformationColumns informationColumns) {
        for (List<String> columns : informationColumns.columns) {
            ColumnNode node = treeColumns.addChildIfNotExist(columns.get(0));
            if (informationColumns.isMultiIndex) {
                for (int j = 1; j < columns.size(); j++) {
                    node = node.addChildIfNotExist(columns.get(j));
                }
            }
        }
    }

    public static final class InformationColumns {
        public boolean isMultiIndex;
        public List<List<String>> columns;
    }

    public static class ColumnNode {
        SmartFMap<String, ColumnNode> children;

        private ColumnNode() {
            this.children = SmartFMap.emptyMap();
        }

        public ColumnNode addChildIfNotExist(String name) {
            if (children.containsKey(name)) {
                return children.get(name);
            }
            else {
                ColumnNode newNode = new ColumnNode();
                children = children.plus(name, newNode);
                return newNode;
            }
        }

        public ColumnNode getChildIfExist(String name) {
            return children.get(name);
        }

        public Set<String> getChildrenName() {
            return children.keySet();
        }
    }
}



