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
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.annotation.access.RequiredReadAction;
import consulo.component.extension.Extensions;
import consulo.content.bundle.Sdk;
import consulo.language.psi.*;
import consulo.language.psi.util.QualifiedName;
import consulo.module.Module;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class QualifiedNameFinder
{
	/**
	 * Looks for a way to import given file.
	 *
	 * @param foothold an element in the file to import to (maybe the file itself); used to determine module, roots, etc.
	 * @param vFile    file which importable name we want to find.
	 * @return a possibly qualified name under which the file may be imported, or null. If there's more than one way (overlapping roots),
	 * the name with fewest qualifiers is selected.
	 */
	@Nullable
	public static String findShortestImportableName(PsiElement foothold, VirtualFile vFile)
	{
		QualifiedName qName = findShortestImportableQName(foothold, vFile);
		return qName == null ? null : qName.toString();
	}

	@Nullable
	public static QualifiedName findShortestImportableQName(@Nullable PsiFileSystemItem fsItem)
	{
		VirtualFile vFile = fsItem != null ? fsItem.getVirtualFile() : null;
		return vFile != null ? findShortestImportableQName(fsItem, vFile) : null;
	}

	@Nullable
	public static QualifiedName findShortestImportableQName(PsiElement foothold, VirtualFile vFile)
	{
		return shortestQName(findImportableQNames(foothold, vFile));
	}

	public static List<QualifiedName> findImportableQNames(PsiElement foothold, VirtualFile vFile)
	{
		PythonPathCache cache = ResolveImportUtil.getPathCache(foothold);
		List<QualifiedName> names = cache != null ? cache.getNames(vFile) : null;
		if(names != null)
		{
			return names;
		}
		PathChoosingVisitor visitor = new PathChoosingVisitor(vFile);
		RootVisitorHost.visitRoots(foothold, visitor);
		List<QualifiedName> results = visitor.getResults();
		if(cache != null)
		{
			cache.putNames(vFile, results);
		}
		return results;
	}

	@Nullable
	private static QualifiedName shortestQName(List<QualifiedName> qNames)
	{
		return qNames.stream().min((o1, o2) -> o1.getComponentCount() - o2.getComponentCount()).orElse(null);
	}

	@Nullable
	public static String findShortestImportableName(Module module, VirtualFile vFile)
	{
		PythonPathCache cache = PythonModulePathCache.getInstance(module);
		List<QualifiedName> names = cache.getNames(vFile);
		if(names != null)
		{
			return names.toString();
		}
		PathChoosingVisitor visitor = new PathChoosingVisitor(vFile);
		RootVisitorHost.visitRoots(module, false, visitor);
		List<QualifiedName> results = visitor.getResults();
		cache.putNames(vFile, results);
		QualifiedName qName = shortestQName(results);
		return qName == null ? null : qName.toString();
	}

	/**
	 * Returns the name through which the specified symbol should be imported. This can be different from the qualified name of the
	 * symbol (the place where a symbol is defined). For example, Python 2.7 unittest defines TestCase in unittest.case module
	 * but it should be imported directly from unittest.
	 *
	 * @param symbol   the symbol to be imported
	 * @param foothold the location where the import statement would be added
	 * @return the qualified name, or null if it wasn't possible to calculate one
	 */
	@Nullable
    @RequiredReadAction
	public static QualifiedName findCanonicalImportPath(PsiElement symbol, @Nullable PsiElement foothold)
	{
		PsiFileSystemItem srcFile = symbol instanceof PsiFileSystemItem fsItem ? fsItem : symbol.getContainingFile();
		if(srcFile == null)
		{
			return null;
		}
		VirtualFile virtualFile = srcFile.getVirtualFile();
		if(virtualFile == null)
		{
			return null;
		}
		if(srcFile instanceof PsiFile srcPsiFile && symbol instanceof PsiNamedElement && !(symbol instanceof PsiFileSystemItem))
		{
			PsiElement topLevel = symbol;
			if(symbol instanceof PyFunction function)
			{
				PyClass containingClass = function.getContainingClass();
				if(containingClass != null)
				{
					topLevel = containingClass;
				}
			}
			PsiDirectory dir = srcPsiFile.getContainingDirectory();
			while(dir != null)
			{
				PsiFile initPy = dir.findFile(PyNames.INIT_DOT_PY);
				if(initPy == null)
				{
					break;
				}
				if(initPy instanceof PyFile initPyFile)
				{
					//noinspection ConstantConditions
					List<RatedResolveResult> resolved = initPyFile.multiResolveName(((PsiNamedElement) topLevel).getName());
					PsiElement finalTopLevel = topLevel;
					if(resolved.stream().anyMatch(r -> r.getElement() == finalTopLevel))
					{
						virtualFile = dir.getVirtualFile();
					}
				}
				dir = dir.getParentDirectory();
			}
		}
		QualifiedName qName = findShortestImportableQName(foothold != null ? foothold : symbol, virtualFile);
		if(qName != null)
		{
			for(PyCanonicalPathProvider provider : Extensions.getExtensions(PyCanonicalPathProvider.EP_NAME))
			{
				QualifiedName restored = provider.getCanonicalPath(qName, foothold);
				if(restored != null)
				{
					return restored;
				}
			}
		}
		return qName;
	}

	@Nullable
	public static String getQualifiedName(PyElement element)
	{
		String name = element.getName();
		if(name != null)
		{
			ScopeOwner owner = ScopeUtil.getScopeOwner(element);
			PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
			if(owner instanceof PyClass pyClass)
			{
				String classQName = pyClass.getQualifiedName();
				if(classQName != null)
				{
					return classQName + "." + name;
				}
			}
			else if(owner instanceof PyFile file)
			{
				if(builtinCache.isBuiltin(element))
				{
					return name;
				}
				else
				{
					VirtualFile virtualFile = file.getVirtualFile();
					if(virtualFile != null)
					{
						String fileQName = findShortestImportableName(element, virtualFile);
						if(fileQName != null)
						{
							return fileQName + "." + name;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Tries to find roots that contain given vFile, and among them the root that contains at the smallest depth.
	 * For equal depth source root is in preference to library.
	 */
	private static class PathChoosingVisitor implements RootVisitor
	{
		@Nullable
		private final VirtualFile myVFile;
		private final List<QualifiedName> myResults = new ArrayList<>();

		private PathChoosingVisitor(VirtualFile file)
		{
			if(!file.isDirectory() && file.getName().equals(PyNames.INIT_DOT_PY))
			{
				myVFile = file.getParent();
			}
			else
			{
				myVFile = file;
			}
		}

		@Override
        public boolean visitRoot(VirtualFile root, Module module, Sdk sdk, boolean isModuleSource)
		{
			if(myVFile != null)
			{
				String relativePath = VirtualFileUtil.getRelativePath(myVFile, root, '/');
				if(relativePath != null && !relativePath.isEmpty())
				{
					List<String> result = StringUtil.split(relativePath, "/");
					if(result.size() > 0)
					{
						result.set(result.size() - 1, FileUtil.getNameWithoutExtension(result.get(result.size() - 1)));
					}
					for(String component : result)
					{
						if(!PyNames.isIdentifier(component))
						{
							return true;
						}
					}
					myResults.add(QualifiedName.fromComponents(result));
				}
			}
			return true;
		}

		public List<QualifiedName> getResults()
		{
			return myResults;
		}
	}
}
