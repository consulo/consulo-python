package com.jetbrains.python.impl.inspections;

import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
public class PyShadowingBuiltinsInspectionState implements InspectionToolState<PyShadowingBuiltinsInspectionState>
{
	public List<String> ignoredNames = new ArrayList<String>();

//	@Override
//	public JComponent createOptionsPanel()
//	{
//		final consulo.ide.impl.idea.codeInspection.ui.ListEditForm form =
//				new consulo.ide.impl.idea.codeInspection.ui.ListEditForm("Ignore built-ins", ignoredNames);
//		return form.getContentPanel();
//	}

	@Nullable
	@Override
	public PyShadowingBuiltinsInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyShadowingBuiltinsInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
