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

package org.consulo.python.buildout.module.extension;

import javax.swing.JComponent;

import org.consulo.module.extension.MutableModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.buildout.BuildoutConfigPanel;

/**
 * @author VISTALL
 * @since 20.10.13.
 */
public class BuildoutMutableModuleExtension extends BuildoutModuleExtension implements MutableModuleExtension<BuildoutModuleExtension>
{
	private BuildoutModuleExtension myOriginalExtension;

	public BuildoutMutableModuleExtension(@NotNull String id, @NotNull Module module, BuildoutModuleExtension buildoutModuleExtension)
	{
		super(id, module);
		myOriginalExtension = buildoutModuleExtension;
	}

	@Nullable
	@Override
	public JComponent createConfigurablePanel(@NotNull ModifiableRootModel modifiableRootModel, @Nullable Runnable runnable)
	{
		return wrapToNorth(new BuildoutConfigPanel(this));
	}

	@Override
	public void setEnabled(boolean b)
	{
		myIsEnabled = b;
	}

	@Override
	public boolean isModified()
	{
		return myIsEnabled != myOriginalExtension.isEnabled() || !Comparing.strEqual(myScriptName, myOriginalExtension.getScriptName());
	}

	public void setScriptName(String s)
	{
		myScriptName = s;
	}

	@Override
	public void commit()
	{
		myOriginalExtension.commit(this);
	}
}
