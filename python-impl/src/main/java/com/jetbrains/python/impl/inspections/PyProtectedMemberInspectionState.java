package com.jetbrains.python.impl.inspections;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.xml.serializer.XmlSerializerUtil;

import org.jspecify.annotations.Nullable;

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
		builder.checkBox(PyLocalize.inspProtectedMemberIgnoreTestFunctions(), () -> ignoreTestFunctions, b -> ignoreTestFunctions = b);
		builder.checkBox(PyLocalize.inspProtectedMemberIgnoreAnnotations(), () -> ignoreAnnotations, b -> ignoreAnnotations = b);
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
