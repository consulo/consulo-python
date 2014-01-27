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

import javax.swing.Icon;

import org.consulo.module.extension.ModuleExtensionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import icons.PythonIcons;

/**
 * @author VISTALL
 * @since 20.10.13.
 */
public class BuildoutModuleExtensionProvider implements ModuleExtensionProvider<BuildoutModuleExtension,BuildoutMutableModuleExtension>
{
	@Nullable
	@Override
	public Icon getIcon()
	{
		return PythonIcons.Python.Buildout.Buildout;
	}

	@NotNull
	@Override
	public String getName()
	{
		return "Buildout";
	}

	@NotNull
	@Override
	public BuildoutModuleExtension createImmutable(@NotNull String s, @NotNull Module module)
	{
		return new BuildoutModuleExtension(s, module);
	}

	@NotNull
	@Override
	public BuildoutMutableModuleExtension createMutable(@NotNull String s, @NotNull Module module)
	{
		return new BuildoutMutableModuleExtension(s, module);
	}
}
