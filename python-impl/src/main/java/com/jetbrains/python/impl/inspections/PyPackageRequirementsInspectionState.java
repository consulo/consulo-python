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
public class PyPackageRequirementsInspectionState implements InspectionToolState<PyPackageRequirementsInspectionState>
{
	public List<String> ignoredPackages = new ArrayList<>();

//	@Override
//	public JComponent createOptionsPanel()
//	{
//		final ListEditForm form = new consulo.ide.impl.idea.codeInspection.ui.ListEditForm("Ignore packages", ignoredPackages);
//		return form.getContentPanel();
//	}

	@Nullable
	@Override
	public PyPackageRequirementsInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyPackageRequirementsInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
