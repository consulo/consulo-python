// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger.dataframe;

import com.jetbrains.python.impl.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.impl.debugger.containerview.PyNumericViewUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.JTable;
import java.awt.Color;


class DataFrameTableCellRenderer extends DataViewCellRenderer implements ColoredCellRenderer {


    private boolean myColored = true;

    DataFrameTableCellRenderer() {
        setHorizontalAlignment(LEFT);
        setHorizontalTextPosition(LEFT);
        setVerticalAlignment(CENTER);
    }

    @Override
    public void setColored(boolean colored) {
        myColored = colored;
    }

    @Override
    protected void colorize(JTable table, @Nullable Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (!(value instanceof TableValueDescriptor descriptor)) {
            this.setBackground(null);
            return;
        }

        Color background = null;

        if (myColored) {
            try {
                double rangedValue = descriptor.getRangedValue();
                if (!Double.isNaN(rangedValue)) {
                    background = PyNumericViewUtil.rangedValueToColor(rangedValue);
                }
            }
            catch (NumberFormatException ignored) {

            }
        }
        this.setBackground(background);
    }
}
