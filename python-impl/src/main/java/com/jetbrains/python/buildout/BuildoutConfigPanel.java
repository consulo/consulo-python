/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.buildout;

import consulo.configurable.ConfigurationException;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.module.Module;
import consulo.python.buildout.module.extension.BuildoutModuleExtension;
import consulo.python.buildout.module.extension.BuildoutMutableModuleExtension;
import consulo.ui.ex.awt.CollectionComboBoxModel;
import consulo.ui.ex.awt.ComboboxWithBrowseButton;
import consulo.ui.ex.awt.TextComponentAccessor;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Panel to choose target buildout script
 * User: dcheryasov
 * Date: Jul 26, 2010 5:09:23 PM
 */
public class BuildoutConfigPanel extends JPanel
{
	private final Module myModule;
	private ComboboxWithBrowseButton myScript;
	private JPanel myPanel;

	private BuildoutMutableModuleExtension myBuildoutMutableModuleExtension;

	public BuildoutConfigPanel(BuildoutMutableModuleExtension buildoutMutableModuleExtension)
	{
		myModule = buildoutMutableModuleExtension.getModule();
		myBuildoutMutableModuleExtension = buildoutMutableModuleExtension;
		setLayout(new BorderLayout());
		add(myPanel);

		FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
		//descriptor.setRoot(myConfiguration.getRoot());
		myScript.addBrowseFolderListener("Choose a buildout script", "Select the target script that will invoke your code", null, descriptor, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT, false);
		myScript.getComboBox().setEditable(true);

		//initErrorValidation();
	}

	@Nonnull
	public static VirtualFile getScriptFile(String script_name) throws ConfigurationException
	{
		VirtualFile script_file = LocalFileSystem.getInstance().findFileByPath(script_name);
		if(script_file == null || script_file.isDirectory())
		{
			throw new ConfigurationException("Invalid script file '" + script_name + "'");
		}
		return script_file;
	}

	public String getScriptName()
	{
		return (String) myScript.getComboBox().getEditor().getItem();
	}

	public void reset()
	{
		final List<File> scriptFiles = BuildoutModuleExtension.getScripts(myBuildoutMutableModuleExtension, myModule.getProject().getBaseDir());
		final List<String> scripts = ContainerUtil.map(scriptFiles, file -> file.getPath());
		myScript.getComboBox().setModel(new CollectionComboBoxModel(scripts, myBuildoutMutableModuleExtension.getScriptName()));
		myScript.getComboBox().getEditor().setItem(myBuildoutMutableModuleExtension.getScriptName());
	}
}
