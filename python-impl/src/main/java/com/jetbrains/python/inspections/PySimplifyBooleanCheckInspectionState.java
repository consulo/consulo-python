package com.jetbrains.python.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 23/04/2023
 */
public class PySimplifyBooleanCheckInspectionState implements InspectionToolState<PySimplifyBooleanCheckInspectionState> {
  public boolean ignoreComparisonToZero = true;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Ignore comparison to zero"),
                     () -> ignoreComparisonToZero,
                     c -> ignoreComparisonToZero = c);
    return null;
  }

  @Nullable
  @Override
  public PySimplifyBooleanCheckInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(PySimplifyBooleanCheckInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
