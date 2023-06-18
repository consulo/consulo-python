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
public class PyPep8InspectionState implements InspectionToolState<PyPep8InspectionState>
{
	public List<String> ignoredErrors = new ArrayList<>();

	//	@Override
//	public JComponent createOptionsPanel()
//	{
//		consulo.ide.impl.idea.codeInspection.ui.ListEditForm form = new consulo.ide.impl.idea.codeInspection.ui.ListEditForm("Ignore errors", ignoredErrors);
//		return form.getContentPanel();
//	}

	@Nullable
	@Override
	public PyPep8InspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyPep8InspectionState state)
	{
		XmlSerializerUtil.copyBean(state,  this);
	}
}
