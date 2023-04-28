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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.component.extension.Extensions;
import consulo.module.Module;
import consulo.content.bundle.Sdk;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyBuiltinCache;

/**
 * @author yole
 */
public class QualifiedNameFinder
{
	/**
	 * Looks for a way to import given file.
	 *
	 * @param foothold an element in the file to import to (maybe the file itself); used to determine module, roots, etc.
	 * @param vfile    file which importable name we want to find.
	 * @return a possibly qualified name under which the file may be imported, or null. If there's more than one way (overlapping roots),
	 * the name with fewest qualifiers is selected.
	 */
	@Nullable
	public static String findShortestImportableName(@Nonnull PsiElement foothold, @Nonnull VirtualFile vfile)
	{
		final QualifiedName qName = findShortestImportableQName(foothold, vfile);
		return qName == null ? null : qName.toString();
	}

	@Nullable
	public static QualifiedName findShortestImportableQName(@Nullable PsiFileSystemItem fsItem)
	{
		VirtualFile vFile = fsItem != null ? fsItem.getVirtualFile() : null;
		return vFile != null ? findShortestImportableQName(fsItem, vFile) : null;
	}

	@Nullable
	public static QualifiedName findShortestImportableQName(@Nonnull PsiElement foothold, @Nonnull VirtualFile vfile)
	{
		return shortestQName(findImportableQNames(foothold, vfile));
	}

	@Nonnull
	public static List<QualifiedName> findImportableQNames(@Nonnull PsiElement foothold, @Nonnull VirtualFile vfile)
	{
		final PythonPathCache cache = ResolveImportUtil.getPathCache(foothold);
		final List<QualifiedName> names = cache != null ? cache.getNames(vfile) : null;
		if(names != null)
		{
			return names;
		}
		PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
		RootVisitorHost.visitRoots(foothold, visitor);
		final List<QualifiedName> results = visitor.getResults();
		if(cache != null)
		{
			cache.putNames(vfile, results);
		}
		return results;
	}

	@Nullable
	private static QualifiedName shortestQName(@Nonnull List<QualifiedName> qNames)
	{
		return qNames.stream().min((o1, o2) -> o1.getComponentCount() - o2.getComponentCount()).orElse(null);
	}

	@Nullable
	public static String findShortestImportableName(Module module, @Nonnull VirtualFile vfile)
	{
		final PythonPathCache cache = PythonModulePathCache.getInstance(module);
		final List<QualifiedName> names = cache.getNames(vfile);
		if(names != null)
		{
			return names.toString();
		}
		PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
		RootVisitorHost.visitRoots(module, false, visitor);
		final List<QualifiedName> results = visitor.getResults();
		cache.putNames(vfile, results);
		final QualifiedName qName = shortestQName(results);
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
	public static QualifiedName findCanonicalImportPath(@Nonnull PsiElement symbol, @Nullable PsiElement foothold)
	{
		PsiFileSystemItem srcfile = symbol instanceof PsiFileSystemItem ? (PsiFileSystemItem) symbol : symbol.getContainingFile();
		if(srcfile == null)
		{
			return null;
		}
		VirtualFile virtualFile = srcfile.getVirtualFile();
		if(virtualFile == null)
		{
			return null;
		}
		if(srcfile instanceof PsiFile && symbol instanceof PsiNamedElement && !(symbol instanceof PsiFileSystemItem))
		{
			PsiElement toplevel = symbol;
			if(symbol instanceof PyFunction)
			{
				final PyClass containingClass = ((PyFunction) symbol).getContainingClass();
				if(containingClass != null)
				{
					toplevel = containingClass;
				}
			}
			PsiDirectory dir = ((PsiFile) srcfile).getContainingDirectory();
			while(dir != null)
			{
				PsiFile initPy = dir.findFile(PyNames.INIT_DOT_PY);
				if(initPy == null)
				{
					break;
				}
				if(initPy instanceof PyFile)
				{
					//noinspection ConstantConditions
					final List<RatedResolveResult> resolved = ((PyFile) initPy).multiResolveName(((PsiNamedElement) toplevel).getName());
					final PsiElement finalTopLevel = toplevel;
					if(resolved.stream().anyMatch(r -> r.getElement() == finalTopLevel))
					{
						virtualFile = dir.getVirtualFile();
					}
				}
				dir = dir.getParentDirectory();
			}
		}
		final QualifiedName qname = findShortestImportableQName(foothold != null ? foothold : symbol, virtualFile);
		if(qname != null)
		{
			for(PyCanonicalPathProvider provider : Extensions.getExtensions(PyCanonicalPathProvider.EP_NAME))
			{
				final QualifiedName restored = provider.getCanonicalPath(qname, foothold);
				if(restored != null)
				{
					return restored;
				}
			}
		}
		return qname;
	}

	@Nullable
	public static String getQualifiedName(@Nonnull PyElement element)
	{
		final String name = element.getName();
		if(name != null)
		{
			final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
			final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
			if(owner instanceof PyClass)
			{
				final String classQName = ((PyClass) owner).getQualifiedName();
				if(classQName != null)
				{
					return classQName + "." + name;
				}
			}
			else if(owner instanceof PyFile)
			{
				if(builtinCache.isBuiltin(element))
				{
					return name;
				}
				else
				{
					final VirtualFile virtualFile = ((PyFile) owner).getVirtualFile();
					if(virtualFile != null)
					{
						final String fileQName = findShortestImportableName(element, virtualFile);
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
	 * Tries to find roots that contain given vfile, and among them the root that contains at the smallest depth.
	 * For equal depth source root is in preference to library.
	 */
	private static class PathChoosingVisitor implements RootVisitor
	{
		@Nullable
		private final VirtualFile myVFile;
		@Nonnull
		private final List<QualifiedName> myResults = new ArrayList<>();

		private PathChoosingVisitor(@Nonnull VirtualFile file)
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

		public boolean visitRoot(VirtualFile root, Module module, Sdk sdk, boolean isModuleSource)
		{
			if(myVFile != null)
			{
				final String relativePath = VfsUtilCore.getRelativePath(myVFile, root, '/');
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

		@Nonnull
		public List<QualifiedName> getResults()
		{
			return myResults;
		}
	}
}
