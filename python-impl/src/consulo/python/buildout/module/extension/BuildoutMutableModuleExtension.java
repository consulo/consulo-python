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

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.buildout.BuildoutConfigPanel;
import consulo.annotations.RequiredDispatchThread;
import consulo.module.extension.MutableModuleExtension;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 20.10.13.
 */
public class BuildoutMutableModuleExtension extends BuildoutModuleExtension implements MutableModuleExtension<BuildoutModuleExtension>
{
	public BuildoutMutableModuleExtension(@NotNull String id, @NotNull ModuleRootLayer module)
	{
		super(id, module);
	}

	@RequiredDispatchThread
	@Nullable
	@Override
	public JComponent createConfigurablePanel(@Nullable Runnable runnable)
	{
		JPanel panel = new JPanel(new VerticalFlowLayout(true, false));
		panel.add(new BuildoutConfigPanel(this));
		return panel;
	}

	@Override
	public void setEnabled(boolean b)
	{
		myIsEnabled = b;
	}

	@Override
	public boolean isModified(@NotNull BuildoutModuleExtension extension)
	{
		return myIsEnabled != extension.isEnabled() || !Comparing.strEqual(myScriptName, extension.getScriptName());
	}

	public void setScriptName(String s)
	{
		myScriptName = s;
	}
}
