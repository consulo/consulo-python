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

package com.jetbrains.python;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import com.intellij.ide.IconDescriptor;
import com.intellij.ide.IconDescriptorUpdater;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import icons.PythonIcons;

/**
 * @author yole
 */
public class PyIconDescriptorUpdater implements IconDescriptorUpdater
{
	@Override
	public void updateIcon(@NotNull IconDescriptor iconDescriptor, @NotNull PsiElement element, int i)
	{
		if(element instanceof PsiDirectory)
		{
			final PsiDirectory directory = (PsiDirectory) element;
			if(directory.findFile(PyNames.INIT_DOT_PY) != null)
			{
				final VirtualFile vFile = directory.getVirtualFile();
				final VirtualFile root = ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getSourceRootForFile(vFile);
				if(!Comparing.equal(root, vFile))
				{
					iconDescriptor.setMainIcon(PlatformIcons.PACKAGE_ICON);
				}
			}
		}
		else if(element instanceof PyClass)
		{
			iconDescriptor.setMainIcon(PlatformIcons.CLASS_ICON);
		}
		else if(element instanceof PyFunction)
		{
			Icon icon = null;
			final Property property = ((PyFunction) element).getProperty();
			if(property != null)
			{
				if(property.getGetter().valueOrNull() == this)
				{
					icon = PythonIcons.Python.PropertyGetter;
				}
				else if(property.getSetter().valueOrNull() == this)
				{
					icon = PythonIcons.Python.PropertySetter;
				}
				else if(property.getDeleter().valueOrNull() == this)
				{
					icon = PythonIcons.Python.PropertyDeleter;
				}
				else
				{
					icon = PlatformIcons.PROPERTY_ICON;
				}
			}
			if(icon != null)
			{
				iconDescriptor.setMainIcon(icon);
			}
			else
			{
				iconDescriptor.setMainIcon(PlatformIcons.METHOD_ICON);
			}
		}
	}
}
