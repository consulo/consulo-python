package com.jetbrains.python.impl.inspections.unresolvedReference;

import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
public class PyUnresolvedReferencesInspectionState implements InspectionToolState<PyUnresolvedReferencesInspectionState>
{
	public List<String> ignoredIdentifiers = new ArrayList<>();

//	@Override
//	public JComponent createOptionsPanel()
//	{
//		final consulo.ide.impl.idea.codeInspection.ui.ListEditForm form =
//				new consulo.ide.impl.idea.codeInspection.ui.ListEditForm("Ignore references", ignoredIdentifiers);
//		return form.getContentPanel();
//	}

	@Nullable
	@Override
	public PyUnresolvedReferencesInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyUnresolvedReferencesInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
