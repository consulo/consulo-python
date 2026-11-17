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
 * Options of {@link PyRedundantParenthesesInspection} (JB {@code getOptionsPane()}).
 */
public class PyRedundantParenthesesInspectionState implements InspectionToolState<PyRedundantParenthesesInspectionState> {
    public boolean myIgnorePercOperator = false;
    public boolean myIgnoreTupleInReturn = false;
    public boolean myIgnoreEmptyBaseClasses = false;

    @Override
    public @Nullable UnnamedConfigurable createConfigurable() {
        ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
        builder.checkBox(PyLocalize.inspRedundantParensIgnoreArgumentOfOperator(), () -> myIgnorePercOperator, v -> myIgnorePercOperator = v);
        builder.checkBox(PyLocalize.inspRedundantParensIgnoreTuples(), () -> myIgnoreTupleInReturn, v -> myIgnoreTupleInReturn = v);
        builder.checkBox(PyLocalize.inspRedundantParensIgnoreEmptyListsOfBaseClasses(), () -> myIgnoreEmptyBaseClasses, v -> myIgnoreEmptyBaseClasses = v);
        return builder.buildUnnamed();
    }

    @Override
    public PyRedundantParenthesesInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(PyRedundantParenthesesInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
