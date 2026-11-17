// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

public class PyTypeCheckerInspectionState implements InspectionToolState<PyTypeCheckerInspectionState> {
    /**
     * When true, an override of a mutable attribute can have a subtype of the inherited type, as in mypy.
     * When false, the override must have the inherited type, as in pyright and pyrefly.
     */
    public boolean allowCovariantAttributeOverrides = false;

    /**
     * When true, checks an override of a base class attribute without a type annotation against the inferred type of the attribute.
     * When false, checks only an override of an annotated attribute.
     */
    public boolean checkInferredAttributeOverrides = false;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.inspTypeCheckerAllowCovariantAttributeOverrides(),
                         () -> allowCovariantAttributeOverrides,
                         b -> allowCovariantAttributeOverrides = b);
        builder.checkBox(PyLocalize.inspTypeCheckerCheckInferredAttributeOverrides(),
                         () -> checkInferredAttributeOverrides,
                         b -> checkInferredAttributeOverrides = b);
        return builder.buildUnnamed();
    }

    @Override
    public PyTypeCheckerInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyTypeCheckerInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
