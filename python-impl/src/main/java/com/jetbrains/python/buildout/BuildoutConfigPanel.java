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

import java.awt.BorderLayout;
import java.io.File;
import java.util.List;

import javax.swing.JPanel;

import consulo.python.buildout.module.extension.BuildoutModuleExtension;
import consulo.python.buildout.module.extension.BuildoutMutableModuleExtension;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

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

	@NotNull
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
		final List<String> scripts = ContainerUtil.map(scriptFiles, new Function<File, String>()
		{
			@Override
			public String fun(File file)
			{
				return file.getPath();
			}
		});
		myScript.getComboBox().setModel(new CollectionComboBoxModel(scripts, myBuildoutMutableModuleExtension.getScriptName()));
		myScript.getComboBox().getEditor().setItem(myBuildoutMutableModuleExtension.getScriptName());
	}
}
