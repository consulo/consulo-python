// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * Options of {@link PyNonAsciiCharInspection} (JB {@code getOptionsPane()}).
 *
 * @author VISTALL
 * @since 27/04/2023
 */
public class PyNonAsciiCharInspectionState implements InspectionToolState<PyNonAsciiCharInspectionState> {
    public String myDefaultEncoding = "utf-8";
    public int myEncodingFormatIndex = 0;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        PyMandatoryEncodingInspection.defaultEncodingDropDown(builder, () -> myDefaultEncoding, v -> myDefaultEncoding = v);
        PyMandatoryEncodingInspection.encodingFormatDropDown(builder, () -> myEncodingFormatIndex, v -> myEncodingFormatIndex = v);
        return builder.buildUnnamed();
    }

    @Override
    public PyNonAsciiCharInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyNonAsciiCharInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
