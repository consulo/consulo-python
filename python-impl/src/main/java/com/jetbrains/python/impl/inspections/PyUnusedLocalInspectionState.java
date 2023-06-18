package com.jetbrains.python.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 25/04/2023
 */
public class PyUnusedLocalInspectionState implements InspectionToolState<PyUnusedLocalInspectionState> {
  public boolean ignoreTupleUnpacking = true;
  public boolean ignoreLambdaParameters = true;
  public boolean ignoreLoopIterationVariables = true;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Ignore variables used in tuple unpacking"),
                     () -> ignoreTupleUnpacking,
                     b -> ignoreTupleUnpacking = b);
    builder.checkBox(LocalizeValue.localizeTODO("Ignore lambda parameters"), () -> ignoreLambdaParameters, b -> ignoreLambdaParameters = b);
    builder.checkBox(LocalizeValue.localizeTODO("Ignore range iteration variables"),
                     () -> ignoreLoopIterationVariables,
                     b -> ignoreLoopIterationVariables = b);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public PyUnusedLocalInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(PyUnusedLocalInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
