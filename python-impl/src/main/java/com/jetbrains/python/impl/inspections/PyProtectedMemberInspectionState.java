package com.jetbrains.python.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
public class PyProtectedMemberInspectionState implements InspectionToolState<PyProtectedMemberInspectionState>
{
	public boolean ignoreTestFunctions = true;
	public boolean ignoreAnnotations = false;

	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		builder.checkBox(LocalizeValue.localizeTODO("Ignore test functions"), () -> ignoreTestFunctions, b -> ignoreTestFunctions = b);
		builder.checkBox(LocalizeValue.localizeTODO("Ignore annotations"), () -> ignoreAnnotations, b -> ignoreAnnotations = b);
		return builder.buildUnnamed();
	}

	@Nullable
	@Override
	public PyProtectedMemberInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyProtectedMemberInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
