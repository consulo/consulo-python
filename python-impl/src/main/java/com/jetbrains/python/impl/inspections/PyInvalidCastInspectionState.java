// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * Options of {@link PyInvalidCastInspection} (JB {@code getOptionsPane()}).
 */
public class PyInvalidCastInspectionState implements InspectionToolState<PyInvalidCastInspectionState> {
    public boolean ignoreGenericVariance = true;

    public boolean ignoreTypedDictStructure = true;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.inspInvalidCastIgnoreGenericVariance(),
                         () -> ignoreGenericVariance,
                         b -> ignoreGenericVariance = b);
        builder.checkBox(PyLocalize.inspInvalidCastIgnoreTypedDictStructure(),
                         () -> ignoreTypedDictStructure,
                         b -> ignoreTypedDictStructure = b);
        return builder.buildUnnamed();
    }

    @Override
    public PyInvalidCastInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyInvalidCastInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
