package com.jetbrains.python.inspections;

import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 26/04/2023
 */
public class PyMethodParametersInspectionState implements InspectionToolState<PyMethodParametersInspectionState>
{
	public String MCS = "mcs";

// TODO
//	@Nullable
//	@Override
//	public JComponent createOptionsPanel()
//	{
//		ComboBox comboBox = new ComboBox(new String[]{
//				"mcs",
//				"metacls"
//		});
//		comboBox.setSelectedItem(MCS);
//		comboBox.addActionListener(new ActionListener()
//		{
//			@Override
//			public void actionPerformed(ActionEvent e)
//			{
//				ComboBox cb = (ComboBox) e.getSource();
//				MCS = (String) cb.getSelectedItem();
//			}
//		});
//
//		JPanel option = new JPanel(new BorderLayout());
//		option.add(new JLabel("Metaclass method first argument name"), BorderLayout.WEST);
//		option.add(comboBox, BorderLayout.EAST);
//
//		final JPanel root = new JPanel(new BorderLayout());
//		root.add(option, BorderLayout.PAGE_START);
//		return root;
//	}

	@Nullable
	@Override
	public PyMethodParametersInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyMethodParametersInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
