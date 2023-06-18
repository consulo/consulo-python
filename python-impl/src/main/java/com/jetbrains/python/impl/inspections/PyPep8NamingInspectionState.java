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
public class PyPep8NamingInspectionState implements InspectionToolState<PyPep8NamingInspectionState>
{
	public final List<String> ignoredErrors = new ArrayList<>();

	public boolean ignoreOverriddenFunctions = true;

	public final List<String> ignoredBaseClasses = new ArrayList<>(List.of("unittest.TestCase", "unittest.case.TestCase"));

	//	@Nullable
//	@Override
//	public JComponent createOptionsPanel()
//	{
//		final JPanel rootPanel = new JPanel(new BorderLayout());
//		rootPanel.add(new CheckBox("Ignore overridden functions", this, "ignoreOverriddenFunctions"), BorderLayout.NORTH);
//
//		final OnePixelSplitter splitter = new OnePixelSplitter(false);
//		splitter.setFirstComponent(new consulo.ide.impl.idea.codeInspection.ui.ListEditForm("Excluded base classes",
//				ignoredBaseClasses).getContentPanel());
//		splitter.setSecondComponent(new ListEditForm("Ignored errors", ignoredErrors).getContentPanel());
//		rootPanel.add(splitter, BorderLayout.CENTER);
//
//		return rootPanel;
//	}

	@Nullable
	@Override
	public PyPep8NamingInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyPep8NamingInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
