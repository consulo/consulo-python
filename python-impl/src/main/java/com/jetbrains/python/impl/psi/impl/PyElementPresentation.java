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
package com.jetbrains.python.impl.psi.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.ui.ex.ColoredItemPresentation;
import consulo.colorScheme.TextAttributesKey;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.ui.image.Image;

/**
 * @author vlan
 */
public class PyElementPresentation implements ColoredItemPresentation
{
	@Nonnull
	private final PyElement myElement;

	public PyElementPresentation(@Nonnull PyElement element)
	{
		myElement = element;
	}

	@Nullable
	@Override
	public TextAttributesKey getTextAttributesKey()
	{
		return null;
	}

	@Nullable
	@Override
	public String getPresentableText()
	{
		final String name = myElement.getName();
		return name != null ? name : PyNames.UNNAMED_ELEMENT;
	}

	@Nullable
	@Override
	public String getLocationString()
	{
		return "(" + getPackageForFile(myElement.getContainingFile()) + ")";
	}

	@Nullable
	@Override
	public Image getIcon()
	{
		return IconDescriptorUpdaters.getIcon(myElement, 0);
	}

	public static String getPackageForFile(@Nonnull PsiFile containingFile)
	{
		final VirtualFile vFile = containingFile.getVirtualFile();

		if(vFile != null)
		{
			final String importableName = QualifiedNameFinder.findShortestImportableName(containingFile, vFile);
			if(importableName != null)
			{
				return importableName;
			}
		}
		return "";
	}
}
