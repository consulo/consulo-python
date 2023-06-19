package com.jetbrains.python.rest.inspections;

import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 28/04/2023
 */
public class RestRoleInspectionState implements InspectionToolState<RestRoleInspectionState>
{
	public List<String> ignoredRoles = new ArrayList<>();

//	@Override
//	public JComponent createOptionsPanel()
//	{
//		consulo.ide.impl.idea.codeInspection.ui.ListEditForm form = new consulo.ide.impl.idea.codeInspection.ui.ListEditForm("Ignore roles", ignoredRoles);
//		return form.getContentPanel();
//	}

	@Nullable
	@Override
	public RestRoleInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(RestRoleInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
