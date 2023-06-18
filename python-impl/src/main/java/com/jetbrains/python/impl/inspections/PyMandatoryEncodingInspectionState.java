package com.jetbrains.python.impl.inspections;

import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
public class PyMandatoryEncodingInspectionState implements InspectionToolState<PyMandatoryEncodingInspectionState>
{
	public String myDefaultEncoding = "utf-8";
	public int myEncodingFormatIndex = 0;

	public String getDefaultEncoding()
	{
		return myDefaultEncoding;
	}

	public void setDefaultEncoding(String defaultEncoding)
	{
		myDefaultEncoding = defaultEncoding;
	}

	public int getEncodingFormatIndex()
	{
		return myEncodingFormatIndex;
	}

	public void setEncodingFormatIndex(int encodingFormatIndex)
	{
		myEncodingFormatIndex = encodingFormatIndex;
	}

	//	@Override
	//	public JComponent createOptionsPanel()
	//	{
	//		final JComboBox defaultEncoding = new JComboBox(PyEncodingUtil.POSSIBLE_ENCODINGS);
	//		defaultEncoding.setSelectedItem(myDefaultEncoding);
	//
	//		defaultEncoding.addActionListener(new ActionListener()
	//		{
	//			@Override
	//			public void actionPerformed(ActionEvent e)
	//			{
	//				JComboBox cb = (JComboBox) e.getSource();
	//				myDefaultEncoding = (String) cb.getSelectedItem();
	//			}
	//		});
	//
	//		final JComboBox encodingFormat = new JComboBox(PyEncodingUtil.ENCODING_FORMAT);
	//
	//		encodingFormat.setSelectedIndex(myEncodingFormatIndex);
	//		encodingFormat.addActionListener(new ActionListener()
	//		{
	//			@Override
	//			public void actionPerformed(ActionEvent e)
	//			{
	//				JComboBox cb = (JComboBox) e.getSource();
	//				myEncodingFormatIndex = cb.getSelectedIndex();
	//			}
	//		});
	//
	//		return PyEncodingUtil.createEncodingOptionsPanel(defaultEncoding, encodingFormat);
	//	}

	@Nullable
	@Override
	public PyMandatoryEncodingInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(PyMandatoryEncodingInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
