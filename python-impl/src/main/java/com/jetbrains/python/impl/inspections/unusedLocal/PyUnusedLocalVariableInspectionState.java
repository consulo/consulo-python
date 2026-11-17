// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unusedLocal;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * Options of {@link PyUnusedLocalVariableInspection} (JB keeps them as fields of the inspection).
 */
public class PyUnusedLocalVariableInspectionState implements InspectionToolState<PyUnusedLocalVariableInspectionState> {
    public boolean ignoreTupleUnpacking = true;
    public boolean ignoreLoopIterationVariables = true;
    public boolean ignoreVariablesStartingWithUnderscore = true;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.inspUnusedLocalsIgnoreVariablesUsedInTupleUnpacking(),
                         () -> ignoreTupleUnpacking,
                         b -> ignoreTupleUnpacking = b);
        builder.checkBox(PyLocalize.inspUnusedLocalsIgnoreRangeIterationVariables(),
                         () -> ignoreLoopIterationVariables,
                         b -> ignoreLoopIterationVariables = b);
        builder.checkBox(PyLocalize.inspUnusedLocalsIgnoreVariablesStartingWith(),
                         () -> ignoreVariablesStartingWithUnderscore,
                         b -> ignoreVariablesStartingWithUnderscore = b);
        return builder.buildUnnamed();
    }

    @Override
    public PyUnusedLocalVariableInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyUnusedLocalVariableInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
