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
 * Options of {@link PyUnusedParameterInspection} (JB keeps them as fields of the inspection).
 */
public class PyUnusedParameterInspectionState implements InspectionToolState<PyUnusedParameterInspectionState> {
    public boolean ignoreLambdaParameters = true;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.inspUnusedLocalsIgnoreLambdaParameters(),
                         () -> ignoreLambdaParameters,
                         b -> ignoreLambdaParameters = b);
        return builder.buildUnnamed();
    }

    @Override
    public PyUnusedParameterInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyUnusedParameterInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
