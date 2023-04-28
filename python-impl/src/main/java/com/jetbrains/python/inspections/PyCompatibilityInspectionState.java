package com.jetbrains.python.inspections;

import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.collection.ContainerUtil;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
public class PyCompatibilityInspectionState implements InspectionToolState<PyCompatibilityInspectionState>
{
	public List<String> versions = new ArrayList<>();

	public PyCompatibilityInspectionState()
	{
		versions.addAll(ContainerUtil.map(PyCompatibilityInspection.DEFAULT_PYTHON_VERSIONS, LanguageLevel::toString));
	}

//	@Override
//	public JComponent createOptionsPanel()
//	{
//		final ElementsChooser<String> chooser = new ElementsChooser<>(true);
//		chooser.setElements(UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS, false);
//		chooser.markElements(myVersions);
//		chooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<String>()
//		{
//			@Override
//			public void elementMarkChanged(String element, boolean isMarked)
//			{
//				myVersions.clear();
//				myVersions.addAll(chooser.getMarkedElements());
//			}
//		});
//		final JPanel versionPanel = new JPanel(new BorderLayout());
//		JLabel label = new JLabel("Check for compatibility with python versions:");
//		label.setLabelFor(chooser);
//		versionPanel.add(label, BorderLayout.PAGE_START);
//		versionPanel.add(chooser);
//		return versionPanel;
//	}

	@Nullable
	@Override
	public PyCompatibilityInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyCompatibilityInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
