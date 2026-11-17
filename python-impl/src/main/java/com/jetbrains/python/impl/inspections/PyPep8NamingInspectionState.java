// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Options of {@link PyPep8NamingInspection} (JB {@code getOptionsPane()}).
 *
 * @author VISTALL
 * @since 27/04/2023
 */
public class PyPep8NamingInspectionState implements InspectionToolState<PyPep8NamingInspectionState> {
    public final List<String> ignoredErrors = new ArrayList<>();

    public boolean ignoreOverriddenFunctions = true;

    public final List<String> ignoredBaseClasses = new ArrayList<>(List.of("unittest.TestCase", "unittest.case.TestCase"));

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.ignoreOverriddenFunctions(), () -> ignoreOverriddenFunctions, b -> ignoreOverriddenFunctions = b);
        // TODO consulo: string list editor missing in ConfigurableBuilder
        //  (JB: stringList("ignoredBaseClasses", INSP.pep8.naming.column.name.excluded.base.classes),
        //       stringList("ignoredErrors", INSP.pep8.naming.column.name.ignored.errors))
        return builder.buildUnnamed();
    }

    @Override
    public PyPep8NamingInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyPep8NamingInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
