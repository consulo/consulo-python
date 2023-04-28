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
 * @since 25/04/2023
 */
public class PyRedundantParenthesesInspectionState implements InspectionToolState<PyRedundantParenthesesInspectionState> {
  public boolean myIgnorePercOperator = false;
  public boolean myIgnoreTupleInReturn = false;

  public boolean isIgnorePercOperator() {
    return myIgnorePercOperator;
  }

  public void setIgnorePercOperator(boolean ignorePercOperator) {
    myIgnorePercOperator = ignorePercOperator;
  }

  public boolean isIgnoreTupleInReturn() {
    return myIgnoreTupleInReturn;
  }

  public void setIgnoreTupleInReturn(boolean ignoreTupleInReturn) {
    myIgnoreTupleInReturn = ignoreTupleInReturn;
  }

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Ignore argument of % operator"), this::isIgnorePercOperator, this::setIgnorePercOperator);
    builder.checkBox(LocalizeValue.localizeTODO("Ignore tuple in return statement"),
                     this::isIgnoreTupleInReturn,
                     this::setIgnoreTupleInReturn);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public PyRedundantParenthesesInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(PyRedundantParenthesesInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
