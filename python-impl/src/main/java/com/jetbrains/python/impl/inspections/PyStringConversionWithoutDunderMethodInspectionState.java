// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Options of {@link PyStringConversionWithoutDunderMethodInspection} (JB keeps them as fields of the inspection).
 */
public class PyStringConversionWithoutDunderMethodInspectionState
    implements InspectionToolState<PyStringConversionWithoutDunderMethodInspectionState> {
    public List<String> ignoredTypes = new ArrayList<>();

    public List<String> reportedTypes = new ArrayList<>(List.of(
        PyNames.FQN.OBJECT, PyNames.FQN.FUNCTION_TYPE, PyNames.FQN.TYPE
    ));

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        // JB: OptPane.pane(
        //   OptPane.stringList("ignoredTypes", PyPsiBundle.message("INSP.string.conversion.ignored.types")),
        //   OptPane.stringList("reportedTypes", PyPsiBundle.message("INSP.string.conversion.reported.types")))
        // TODO consulo: string list option control for ConfigurableBuilder missing
        return null;
    }

    @Override
    public PyStringConversionWithoutDunderMethodInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyStringConversionWithoutDunderMethodInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
