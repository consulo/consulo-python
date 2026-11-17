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
 * Options of {@link PyMissingTypeHintsInspection} (JB {@code getOptionsPane()}).
 */
public class PyMissingTypeHintsInspectionState implements InspectionToolState<PyMissingTypeHintsInspectionState> {
    /**
     * @noinspection PublicField
     */
    public boolean m_onlyWhenTypesAreKnown = true;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.inspMissingTypeHintsCheckboxOnlyWhenTypesAreKnown(),
                         () -> m_onlyWhenTypesAreKnown,
                         b -> m_onlyWhenTypesAreKnown = b);
        return builder.buildUnnamed();
    }

    @Override
    public PyMissingTypeHintsInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyMissingTypeHintsInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
