/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.refactoring.classes.extractSuperclass;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.TextComponentAccessor;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassViewSwingImpl extends MembersBasedViewSwingImpl<PyExtractSuperclassPresenter, PyExtractSuperclassInitializationInfo> implements PyExtractSuperclassView
{
	private static final String FILE_OR_DIRECTORY = RefactoringBundle.message("extract.superclass.elements.header");
	@Nonnull
	private final JTextArea myExtractedSuperNameField = new JTextArea();
	@Nonnull
	private final FileChooserDescriptor myFileChooserDescriptor;
	@Nonnull
	private final TextFieldWithBrowseButton myTargetDirField;

	PyExtractSuperclassViewSwingImpl(@Nonnull PyClass classUnderRefactoring, @Nonnull Project project, @Nonnull PyExtractSuperclassPresenter presenter)
	{
		super(project, presenter, RefactoringBundle.message("extract.superclass.from"), true);
		setTitle(PyExtractSuperclassHandler.REFACTORING_NAME);


		Box box = Box.createVerticalBox();

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JLabel(RefactoringBundle.message("extract.superclass.from")), BorderLayout.NORTH);
		JTextField sourceClassField = new JTextField();
		sourceClassField.setEditable(false);
		sourceClassField.setText(classUnderRefactoring.getName());
		panel.add(sourceClassField, BorderLayout.CENTER);
		box.add(panel);

		box.add(Box.createVerticalStrut(10));

		JLabel superNameLabel = new JLabel();
		superNameLabel.setText(RefactoringBundle.message("superclass.name"));

		panel = new JPanel(new BorderLayout());
		panel.add(superNameLabel, BorderLayout.NORTH);
		panel.add(myExtractedSuperNameField, BorderLayout.CENTER);
		box.add(panel);
		box.add(Box.createVerticalStrut(5));

		myFileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();


		myFileChooserDescriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());
		myFileChooserDescriptor.withTreeRootVisible(true);
		myTargetDirField = new TextFieldWithBrowseButton();
		myTargetDirField.addBrowseFolderListener(FILE_OR_DIRECTORY, null, project, myFileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

		panel = new JPanel(new BorderLayout());
		JLabel dirLabel = new JLabel();
		dirLabel.setText(FILE_OR_DIRECTORY); //u18n

		panel.add(dirLabel, BorderLayout.NORTH);
		panel.add(myTargetDirField, BorderLayout.CENTER);
		box.add(panel);

		box.add(Box.createVerticalStrut(10));


		myTopPanel.add(box, BorderLayout.CENTER);
		myCenterPanel.add(myPyMemberSelectionPanel, BorderLayout.CENTER);
		setPreviewResults(false);
	}

	@Override
	public JComponent getPreferredFocusedComponent()
	{
		return myExtractedSuperNameField;
	}

	@Override
	public void configure(@Nonnull PyExtractSuperclassInitializationInfo configInfo)
	{
		super.configure(configInfo);
		myFileChooserDescriptor.setRoots(configInfo.getRoots());
		myTargetDirField.setText(configInfo.getDefaultFilePath());
	}

	@Nonnull
	@Override
	public String getModuleFile()
	{
		return myTargetDirField.getText();
	}

	@Nonnull
	@Override
	public String getSuperClassName()
	{
		return myExtractedSuperNameField.getText();
	}

	@Nullable
	@Override
	protected String getHelpId()
	{
		return "refactoring.extract.superclass.dialog";
	}
}
