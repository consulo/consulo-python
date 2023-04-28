/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import consulo.content.bundle.Sdk;
import consulo.execution.ui.awt.EnvironmentVariablesComponent;
import consulo.execution.ui.awt.RawCommandLineEditor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.ide.impl.idea.execution.util.PathMappingsComponent;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.ui.HideableDecorator;
import consulo.ide.impl.idea.util.PathMappingSettings;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModulesAlphaComparator;
import consulo.module.ui.awt.ModuleListCellRenderer;
import consulo.module.ui.awt.SdkComboBox;
import consulo.project.Project;
import consulo.python.module.extension.PyModuleExtension;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.util.io.FileUtil;
import consulo.util.lang.function.Conditions;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyPluginCommonOptionsForm implements AbstractPyCommonOptionsForm
{
	private final Project myProject;
	private TextFieldWithBrowseButton myWorkingDirectoryTextField;
	private EnvironmentVariablesComponent myEnvsComponent;
	private RawCommandLineEditor myInterpreterOptionsTextField;
	private SdkComboBox myInterpreterComboBox;
	private JRadioButton myUseModuleSdkRadioButton;
	private JComboBox<Module> myModuleComboBox;
	private JPanel myMainPanel;
	private JRadioButton myUseSpecifiedSdkRadioButton;
	private JBLabel myPythonInterpreterJBLabel;
	private JBLabel myInterpreterOptionsJBLabel;
	private JBLabel myWorkingDirectoryJBLabel;
	private JPanel myHideablePanel;
	private PathMappingsComponent myPathMappingsComponent;
	private JBCheckBox myAddContentRootsCheckbox;
	private JBCheckBox myAddSourceRootsCheckbox;
	private JComponent labelAnchor;
	private final HideableDecorator myDecorator;

	public PyPluginCommonOptionsForm(PyCommonOptionsFormData data)
	{
		// setting modules
		myProject = data.getProject();
		final List<Module> validModules = data.getValidModules();
		Collections.sort(validModules, new ModulesAlphaComparator());
		Module selection = validModules.size() > 0 ? validModules.get(0) : null;
		for(Module validModule : validModules)
		{
			myModuleComboBox.addItem(validModule);
		}
		myModuleComboBox.setRenderer(new ModuleListCellRenderer());
		myModuleComboBox.setSelectedItem(selection);

		myWorkingDirectoryTextField.addBrowseFolderListener("Select Working Directory", "", data.getProject(), FileChooserDescriptorFactory.createSingleFolderDescriptor());

		ActionListener listener = e -> updateControls();
		myUseSpecifiedSdkRadioButton.addActionListener(listener);
		myUseModuleSdkRadioButton.addActionListener(listener);
		myInterpreterComboBox.addActionListener(listener);
		myModuleComboBox.addActionListener(listener);

		setAnchor(myEnvsComponent.getLabel());


		myDecorator = new HideableDecorator(myHideablePanel, "Environment", false)
		{
			@Override
			protected void on()
			{
				super.on();
				storeState();
			}

			@Override
			protected void off()
			{
				super.off();
				storeState();
			}

			private void storeState()
			{
				PropertiesComponent.getInstance().setValue(EXPAND_PROPERTY_KEY, String.valueOf(isExpanded()), "true");
			}
		};
		myDecorator.setOn(PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, true));
		myDecorator.setContentComponent(myMainPanel);
		myPathMappingsComponent.setAnchor(myEnvsComponent.getLabel());
		updateControls();
	}

	private void updateControls()
	{
		myModuleComboBox.setEnabled(myUseModuleSdkRadioButton.isSelected());
		myInterpreterComboBox.setEnabled(myUseSpecifiedSdkRadioButton.isSelected());
		myPathMappingsComponent.setVisible(PySdkUtil.isRemote(getSelectedSdk()));
	}

	public JPanel getMainPanel()
	{
		return myHideablePanel;
	}

	@Override
	public void subscribe()
	{
	}

	@Override
	public void addInterpreterComboBoxActionListener(ActionListener listener)
	{
		myInterpreterComboBox.addActionListener(listener);
	}

	@Override
	public void removeInterpreterComboBoxActionListener(ActionListener listener)
	{
		myInterpreterComboBox.removeActionListener(listener);
	}

	public String getInterpreterOptions()
	{
		return myInterpreterOptionsTextField.getText().trim();
	}

	public void setInterpreterOptions(String interpreterOptions)
	{
		myInterpreterOptionsTextField.setText(interpreterOptions);
	}

	public String getWorkingDirectory()
	{
		return FileUtil.toSystemIndependentName(myWorkingDirectoryTextField.getText().trim());
	}

	public void setWorkingDirectory(String workingDirectory)
	{
		myWorkingDirectoryTextField.setText(workingDirectory == null ? "" : FileUtil.toSystemDependentName(workingDirectory));
	}

	@Nullable
	public String getSdkHome()
	{
		Sdk selectedSdk = myInterpreterComboBox.getSelectedSdk();
		return selectedSdk == null ? null : selectedSdk.getHomePath();
	}

	public void setSdkHome(String sdkHome)
	{
		myInterpreterComboBox.setSelectedSdk(sdkHome);
	}

	public Module getModule()
	{
		return (Module) myModuleComboBox.getSelectedItem();
	}

	@Override
	public String getModuleName()
	{
		Module module = getModule();
		return module != null ? module.getName() : null;
	}

	public void setModule(Module module)
	{
		myModuleComboBox.setSelectedItem(module);
	}

	public boolean isUseModuleSdk()
	{
		return myUseModuleSdkRadioButton.isSelected();
	}

	public void setUseModuleSdk(boolean useModuleSdk)
	{
		if(useModuleSdk)
		{
			myUseModuleSdkRadioButton.setSelected(true);
		}
		else
		{
			myUseSpecifiedSdkRadioButton.setSelected(true);
		}
		updateControls();
	}

	public boolean isPassParentEnvs()
	{
		return myEnvsComponent.isPassParentEnvs();
	}

	public void setPassParentEnvs(boolean passParentEnvs)
	{
		myEnvsComponent.setPassParentEnvs(passParentEnvs);
	}

	public Map<String, String> getEnvs()
	{
		return myEnvsComponent.getEnvs();
	}

	public void setEnvs(Map<String, String> envs)
	{
		myEnvsComponent.setEnvs(envs);
	}

	@Override
	public PathMappingSettings getMappingSettings()
	{
		return myPathMappingsComponent.getMappingSettings();
	}

	@Override
	public void setMappingSettings(@Nullable PathMappingSettings mappingSettings)
	{
		myPathMappingsComponent.setMappingSettings(mappingSettings);
	}

	private Sdk getSelectedSdk()
	{
		if(isUseModuleSdk())
		{
			Module module = getModule();
			return module == null ? null : ModuleUtilCore.getSdk(module, PyModuleExtension.class);
		}
		Sdk sdk = myInterpreterComboBox.getSelectedSdk();
		if(sdk == null)
		{
			return null;
		}
		return sdk;
	}

	@Override
	public JComponent getAnchor()
	{
		return labelAnchor;
	}

	@Override
	public void setAnchor(JComponent anchor)
	{
		labelAnchor = anchor;
		myPythonInterpreterJBLabel.setAnchor(anchor);
		myInterpreterOptionsJBLabel.setAnchor(anchor);
		myWorkingDirectoryJBLabel.setAnchor(anchor);
		myEnvsComponent.setAnchor(anchor);
	}

	@Override
	public boolean shouldAddContentRoots()
	{
		return myAddContentRootsCheckbox.isSelected();
	}

	@Override
	public boolean shouldAddSourceRoots()
	{
		return myAddSourceRootsCheckbox.isSelected();
	}

	@Override
	public void setAddContentRoots(boolean flag)
	{
		myAddContentRootsCheckbox.setSelected(flag);
	}

	@Override
	public void setAddSourceRoots(boolean flag)
	{
		myAddSourceRootsCheckbox.setSelected(flag);
	}

	private void createUIComponents()
	{
		consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel model = new consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel();
		model.reset();

		myInterpreterComboBox = new SdkComboBox(model, Conditions.equalTo(PythonSdkType.getInstance()), true);
	}
}
