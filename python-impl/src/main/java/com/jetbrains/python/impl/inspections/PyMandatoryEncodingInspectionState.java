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
 * Options of {@link PyMandatoryEncodingInspection} (JB {@code getOptionsPane()}).
 */
public class PyMandatoryEncodingInspectionState implements InspectionToolState<PyMandatoryEncodingInspectionState> {
    public String myDefaultEncoding = "utf-8";
    public int myEncodingFormatIndex = 0;
    public boolean myAllPythons = false;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.inspMandatoryEncodingCheckboxEnableInPython3(), () -> myAllPythons, b -> myAllPythons = b);
        PyMandatoryEncodingInspection.defaultEncodingDropDown(builder, () -> myDefaultEncoding, v -> myDefaultEncoding = v);
        PyMandatoryEncodingInspection.encodingFormatDropDown(builder, () -> myEncodingFormatIndex, v -> myEncodingFormatIndex = v);
        return builder.buildUnnamed();
    }

    @Override
    public PyMandatoryEncodingInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyMandatoryEncodingInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
