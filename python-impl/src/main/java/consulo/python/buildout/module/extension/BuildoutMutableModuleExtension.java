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

package consulo.python.buildout.module.extension;

import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.MutableModuleExtension;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;

import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 20.10.13.
 */
public class BuildoutMutableModuleExtension extends BuildoutModuleExtension implements MutableModuleExtension<BuildoutModuleExtension>
{
	public BuildoutMutableModuleExtension(String id, ModuleRootLayer module)
	{
		super(id, module);
	}

	@RequiredUIAccess
	@Nullable
	@Override
	public Component createConfigurationComponent(Disposable disposable, Runnable runnable)
	{
		FileChooserTextBoxBuilder.Controller scriptBox = FileChooserTextBoxBuilder.create(getProject())
				.fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
				.dialogTitle(PyLocalize.buildoutScriptChooserTitle())
				.dialogDescription(PyLocalize.buildoutScriptChooserDescription())
				.uiDisposable(disposable)
				.build();
		scriptBox.setValue(StringUtil.notNullize(getScriptName()), false);
		scriptBox.getComponent().addValueListener(event -> setScriptName(scriptBox.getValue()));

		FormBuilder formBuilder = FormBuilder.create();
		formBuilder.addLabeled(PyLocalize.buildoutScriptLabel(), scriptBox.getComponent());
		return formBuilder.build();
	}

	@Override
	public void setEnabled(boolean b)
	{
		myIsEnabled = b;
	}

	@Override
	public boolean isModified(BuildoutModuleExtension extension)
	{
		return myIsEnabled != extension.isEnabled() || !Comparing.strEqual(myScriptName, extension.getScriptName());
	}

	public void setScriptName(String s)
	{
		myScriptName = s;
	}
}
