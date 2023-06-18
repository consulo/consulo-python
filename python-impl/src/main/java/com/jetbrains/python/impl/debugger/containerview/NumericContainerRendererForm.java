/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.debugger.containerview;

import java.awt.event.KeyListener;

import javax.annotation.Nonnull;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import consulo.codeEditor.EditorEx;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.project.Project;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.table.JBTable;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.debugger.array.JBTableWithRowHeaders;

/**
 * Created by Yuli Fiterman on 5/10/2016.
 */
public class NumericContainerRendererForm
{
	protected final Project myProject;
	protected final KeyListener myReformatCallback;
	protected final KeyListener myResliceCallback;
	protected JBScrollPane myScrollPane;
	protected EditorTextField mySliceTextField;
	protected JBTableWithRowHeaders myTable;
	protected EditorTextField myFormatTextField;
	protected JCheckBox myColoredCheckbox;
	protected JPanel myFormatPanel;
	protected JPanel myMainPanel;
	protected JLabel myFormatLabel;

	public NumericContainerRendererForm(@Nonnull Project project, KeyListener resliceCallback, KeyListener reformatCallback)
	{
		myResliceCallback = resliceCallback;
		myProject = project;
		myReformatCallback = reformatCallback;
	}

	public JBTable getTable()
	{
		return myTable;
	}

	public JPanel getMainPanel()
	{
		return myMainPanel;
	}

	public EditorTextField getSliceTextField()
	{
		return mySliceTextField;
	}

	public JCheckBox getColoredCheckbox()
	{
		return myColoredCheckbox;
	}

	public JBScrollPane getScrollPane()
	{
		return myScrollPane;
	}

	public EditorTextField getFormatTextField()
	{
		return myFormatTextField;
	}

	protected void createUIComponents()
	{
		mySliceTextField = new EditorTextField("", myProject, PythonFileType.INSTANCE)
		{
			@Override
			protected EditorEx createEditor()
			{
				EditorEx editor = super.createEditor();
				editor.getContentComponent().addKeyListener(myResliceCallback);
				return editor;
			}
		};

		myFormatTextField = new EditorTextField("", myProject, PythonFileType.INSTANCE)
		{
			@Override
			protected EditorEx createEditor()
			{
				EditorEx editor = super.createEditor();
				editor.getContentComponent().addKeyListener(myReformatCallback);
				return editor;
			}
		};

		myTable = new JBTableWithRowHeaders();
		myScrollPane = myTable.getScrollPane();
	}
}
