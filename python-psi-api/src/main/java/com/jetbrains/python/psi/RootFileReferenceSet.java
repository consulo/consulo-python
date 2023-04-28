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
package com.jetbrains.python.psi;

import consulo.application.ApplicationManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.path.FileReferenceSet;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.ManagingFS;
import consulo.virtualFileSystem.fileType.FileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Resolves absolute paths from FS root, not content roots
 *
 * @author traff
 */
public class RootFileReferenceSet extends FileReferenceSet
{
	public RootFileReferenceSet(String str,
			@Nonnull PsiElement element,
			int startInElement,
			PsiReferenceProvider provider,
			boolean caseSensitive,
			boolean endingSlashNotAllowed,
			@Nullable FileType[] suitableFileTypes)
	{
		super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes);
	}

	public RootFileReferenceSet(String s, @Nonnull PsiElement element, int offset, PsiReferenceProvider provider, boolean sensitive)
	{
		super(s, element, offset, provider, sensitive);
	}

	@Override
	public boolean isAbsolutePathReference()
	{
		if(!ApplicationManager.getApplication().isUnitTestMode())
		{
			return FileUtil.isAbsolute(getPathString());
		}
		else
		{
			return super.isAbsolutePathReference();
		}
	}

	@Nonnull
	@Override
	public Collection<PsiFileSystemItem> computeDefaultContexts()
	{
		PsiFile file = getContainingFile();
		if(file == null)
		{
			return List.of();
		}

		if(isAbsolutePathReference() && !ApplicationManager.getApplication().isUnitTestMode())
		{
			return toFileSystemItems(ManagingFS.getInstance().getLocalRoots());
		}

		return super.computeDefaultContexts();
	}
}