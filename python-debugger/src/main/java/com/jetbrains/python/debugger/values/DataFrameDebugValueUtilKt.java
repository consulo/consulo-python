// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.values;

import com.jetbrains.python.debugger.PyDebugValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java counterpart of the top-level functions declared in JB {@code DataFrameDebugValueUtil.kt}.
 */
public final class DataFrameDebugValueUtilKt {
    private static final Pattern MULTI_INDEX_DATA_REGEX = Pattern.compile("\\[(\\(.*?(?=\\)).*?)+]");
    private static final Pattern MULTI_INDEX_COLUMN_GROUP_REGEX = Pattern.compile("\\((.*?(?=\\).*?))\\)");
    private static final Pattern COLUMN_NAMES_REGEX = Pattern.compile("'(.*?(?<!\\\\))'|\"(.*?(?<!\\\\))\"");

    private DataFrameDebugValueUtilKt() {
    }

    /**
     * This function extracts children-columns of the current sub-table.
     *
     * @param treeColumns   - DataFrame columns in a tree structure
     * @param columnsBefore - list of columns-nodes (path to the current node)
     * @return set of children of an exact node
     * <p>
     * Example: df.foo.bar.ba&lt;caret&gt;, columnsBefore = ["foo", "bar"]
     */
    public static @Nullable Set<String> completeDataFrameColumns(DataFrameDebugValue.ColumnNode treeColumns,
                                                                 List<@Nullable String> columnsBefore) {
        if (!columnsBefore.isEmpty()) {
            DataFrameDebugValue.ColumnNode node = treeColumns.children != null ? treeColumns.children.get(columnsBefore.get(0)) : null;
            if (node == null) {
                return Collections.emptySet();
            }
            for (int i = 1; i < columnsBefore.size(); i++) {
                String column = columnsBefore.get(i);
                if (column == null) {
                    throw new NullPointerException();
                }
                node = node.getChildIfExist(column);
                if (node == null) {
                    return Collections.emptySet();
                }
            }
            return node.getChildrenName();
        }
        else {
            return treeColumns.getChildrenName();
        }
    }

    /**
     * Retrieves column names and index data from {@link PyDebugValue#getValue()}.
     * <p>
     * The {@code value} is produced by _get_df_variable_repr.
     */
    public static DataFrameDebugValue.@Nullable InformationColumns getInformationColumns(String value)
        throws CannotRetrieveColumnDataException {
        String columnsNames = getDataWithColumnsNames(value);
        if (columnsNames == null) {
            throw new CannotRetrieveColumnDataException("Can't retrieve column data");
        }

        DataFrameDebugValue.InformationColumns multiIndexData = getMultiIndexData(columnsNames);
        if (multiIndexData != null) {
            return multiIndexData;
        }

        List<List<String>> columns = new ArrayList<>();
        Matcher matcher = COLUMN_NAMES_REGEX.matcher(columnsNames);
        while (matcher.find()) {
            columns.add(List.of(lastGroupValue(matcher)));
        }

        if (!columns.isEmpty()) {
            DataFrameDebugValue.InformationColumns result = new DataFrameDebugValue.InformationColumns();
            result.isMultiIndex = false;
            result.columns = columns;
            return result;
        }
        else {
            return null;
        }
    }

    /**
     * The value of the last group (the whole match included) that participated in the match.
     */
    private static String lastGroupValue(Matcher matcher) {
        for (int i = matcher.groupCount(); i >= 0; i--) {
            String group = matcher.group(i);
            if (group != null) {
                return group;
            }
        }
        throw new IllegalStateException();
    }

    private static @Nullable String getDataWithColumnsNames(String value) {
        boolean escaped = false;
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        int index = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            inSingleQuotes = (inSingleQuotes ^ (c == '\'' && !escaped)) && !inDoubleQuotes;
            inDoubleQuotes = (inDoubleQuotes ^ (c == '"' && !escaped)) && !inSingleQuotes;
            escaped = !escaped && c == '\\';

            if (!inSingleQuotes && !inDoubleQuotes && c == ']') {
                index = i;
                break;
            }
        }

        if (index != -1) {
            return value.substring(0, index + 1);
        }
        else if (value.endsWith("...")) {
            return value.substring(0, value.length() - 3);
        }
        else {
            return null;
        }
    }

    private static DataFrameDebugValue.@Nullable InformationColumns getMultiIndexData(String columnData) {
        Matcher dataMatcher = MULTI_INDEX_DATA_REGEX.matcher(columnData);
        if (!dataMatcher.find() || dataMatcher.group(0) == null) {
            return null;
        }
        List<List<String>> columns = new ArrayList<>();
        Matcher groupMatcher = MULTI_INDEX_COLUMN_GROUP_REGEX.matcher(columnData);
        while (groupMatcher.find()) {
            List<String> names = new ArrayList<>();
            Matcher namesMatcher = COLUMN_NAMES_REGEX.matcher(groupMatcher.group());
            while (namesMatcher.find()) {
                names.add(lastGroupValue(namesMatcher));
            }
            columns.add(names);
        }

        if (!columns.isEmpty()) {
            DataFrameDebugValue.InformationColumns result = new DataFrameDebugValue.InformationColumns();
            result.isMultiIndex = true;
            result.columns = columns;
            return result;
        }
        else {
            return null;
        }
    }
}
