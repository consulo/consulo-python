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
package com.jetbrains.python.psi.resolve;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileCopyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;

/**
 * @author yole
 */
public abstract class PythonPathCache
{
	private final Map<QualifiedName, List<PsiElement>> myCache = new HashMap<QualifiedName, List<PsiElement>>();
	private final Map<VirtualFile, List<QualifiedName>> myQNameCache = new HashMap<VirtualFile, List<QualifiedName>>();

	public void clearCache()
	{
		myCache.clear();
		myQNameCache.clear();
	}

	public synchronized List<PsiElement> get(QualifiedName qualifiedName)
	{
		return myCache.get(qualifiedName);
	}

	public synchronized void put(QualifiedName qualifiedName, List<PsiElement> results)
	{
		myCache.put(qualifiedName, results);
	}

	public synchronized List<QualifiedName> getNames(VirtualFile vFile)
	{
		return myQNameCache.get(vFile);
	}

	public synchronized void putNames(VirtualFile vFile, List<QualifiedName> qNames)
	{
		myQNameCache.put(vFile, qNames);
	}

	protected class MyVirtualFileAdapter extends VirtualFileAdapter
	{
		@Override
		public void fileCreated(@Nonnull VirtualFileEvent event)
		{
			clearCache();
		}

		@Override
		public void fileDeleted(@Nonnull VirtualFileEvent event)
		{
			clearCache();
		}

		@Override
		public void fileMoved(@Nonnull VirtualFileMoveEvent event)
		{
			clearCache();
		}

		@Override
		public void fileCopied(@Nonnull VirtualFileCopyEvent event)
		{
			clearCache();
		}

		@Override
		public void propertyChanged(@Nonnull VirtualFilePropertyEvent event)
		{
			if(event.getPropertyName().equals(VirtualFile.PROP_NAME))
			{
				clearCache();
			}
		}
	}
}
