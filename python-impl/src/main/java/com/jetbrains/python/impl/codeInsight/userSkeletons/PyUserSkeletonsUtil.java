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
package com.jetbrains.python.impl.codeInsight.userSkeletons;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.component.extension.Extensions;
import consulo.container.boot.ContainerPathManager;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.QualifiedName;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyUserSkeletonsUtil
{
	public static final String USER_SKELETONS_DIR = "python-skeletons";
	private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil");
	public static final Key<Boolean> HAS_SKELETON = Key.create("PyUserSkeleton.hasSkeleton");

	@Nullable
	private static VirtualFile ourUserSkeletonsDirectory;
	private static boolean ourNoSkeletonsErrorReported = false;

	@Nonnull
	private static List<String> getPossibleUserSkeletonsPaths()
	{
		List<String> result = new ArrayList<>();
		result.add(ContainerPathManager.get().getConfigPath() + File.separator + USER_SKELETONS_DIR);
		result.add(PythonHelpersLocator.getHelperPath(USER_SKELETONS_DIR));
		return result;
	}

	@Nullable
	public static VirtualFile getUserSkeletonsDirectory()
	{
		if(ourUserSkeletonsDirectory == null)
		{
			for(String path : getPossibleUserSkeletonsPaths())
			{
				ourUserSkeletonsDirectory = StandardFileSystems.local().findFileByPath(path);
				if(ourUserSkeletonsDirectory != null)
				{
					break;
				}
			}
		}
		if(!ourNoSkeletonsErrorReported && ourUserSkeletonsDirectory == null)
		{
			ourNoSkeletonsErrorReported = true;
			LOG.warn("python-skeletons directory not found in paths: " + getPossibleUserSkeletonsPaths());
		}
		return ourUserSkeletonsDirectory;
	}

	public static boolean isUnderUserSkeletonsDirectory(@Nonnull PsiFile file)
	{
		VirtualFile virtualFile = file.getVirtualFile();
		if(virtualFile == null)
		{
			return false;
		}
		return isUnderUserSkeletonsDirectory(virtualFile);
	}

	public static boolean isUnderUserSkeletonsDirectory(@Nonnull VirtualFile virtualFile)
	{
		VirtualFile skeletonsDir = getUserSkeletonsDirectory();
		return skeletonsDir != null && VirtualFileUtil.isAncestor(skeletonsDir, virtualFile, false);
	}

	@Nullable
	public static <T extends PyElement> T getUserSkeleton(@Nonnull T element)
	{
		return getUserSkeletonWithContext(element, null);
	}

	@Nullable
	public static <T extends PyElement> T getUserSkeletonWithContext(@Nonnull T element, @Nullable TypeEvalContext context)
	{
		PsiFile file = element.getContainingFile();
		if(file instanceof PyFile)
		{
			PyFile skeletonFile = getUserSkeletonForFile((PyFile) file);
			if(skeletonFile != null && skeletonFile != file)
			{
				PsiElement skeletonElement = getUserSkeleton(element, skeletonFile, context);
				if(element.getClass().isInstance(skeletonElement) && skeletonElement != element)
				{
					//noinspection unchecked
					return (T) skeletonElement;
				}
			}
		}
		return null;
	}

	@Nullable
	public static PyFile getUserSkeletonForModuleQName(@Nonnull String qName, @Nonnull PsiElement foothold)
	{
		Sdk sdk = PythonSdkType.getSdk(foothold);
		if(sdk != null)
		{
			Project project = foothold.getProject();
			PythonSdkPathCache cache = PythonSdkPathCache.getInstance(project, sdk);
			QualifiedName cacheQName = QualifiedName.fromDottedString(USER_SKELETONS_DIR + "." + qName);
			List<PsiElement> results = cache.get(cacheQName);
			if(results != null)
			{
				PsiElement element = results.isEmpty() ? null : results.get(0);
				if(element instanceof PyFile)
				{
					return (PyFile) element;
				}
			}
			VirtualFile directory = getUserSkeletonsDirectory();
			if(directory != null)
			{
				PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
				PsiElement fileSkeleton = new QualifiedNameResolverImpl(qName).resolveModuleAt(psiDirectory);
				if(fileSkeleton instanceof PsiDirectory)
				{
					fileSkeleton = PyUtil.getPackageElement((PsiDirectory) fileSkeleton, foothold);
				}
				if(fileSkeleton instanceof PyFile)
				{
					cache.put(cacheQName, Collections.singletonList(fileSkeleton));
					return (PyFile) fileSkeleton;
				}
			}
			cache.put(cacheQName, Collections.<PsiElement>emptyList());
		}
		return null;
	}

	@Nullable
	private static PsiElement getUserSkeleton(@Nonnull PyElement element, @Nonnull PyFile skeletonFile, @Nullable TypeEvalContext context)
	{
		if(element instanceof PyFile)
		{
			return skeletonFile;
		}
		ScopeOwner owner = ScopeUtil.getScopeOwner(element);
		String name = element.getName();
		if(owner != null && name != null)
		{
			assert owner != element;
			PsiElement originalOwner = getUserSkeleton(owner, skeletonFile, context);
			if(originalOwner instanceof PyClass)
			{
				PyClass classOwner = (PyClass) originalOwner;
				PyType type = TypeEvalContext.codeInsightFallback(classOwner.getProject()).getType(classOwner);
				if(type instanceof PyClassLikeType)
				{
					PyClassLikeType classType = (PyClassLikeType) type;
					PyClassLikeType instanceType = classType.toInstance();
					PyResolveContext resolveContext = PyResolveContext.noImplicits();
					if(context != null)
					{
						resolveContext = resolveContext.withTypeEvalContext(context);
					}
					List<? extends RatedResolveResult> resolveResults = instanceType.resolveMember(name, null, AccessDirection.READ, resolveContext, false);
					if(resolveResults != null && !resolveResults.isEmpty())
					{
						return resolveResults.get(0).getElement();
					}
				}
			}
			else if(originalOwner instanceof PyFile)
			{
				return ((PyFile) originalOwner).getElementNamed(name);
			}
		}
		return null;
	}

	@Nullable
	private static PyFile getUserSkeletonForFile(@Nonnull PyFile file)
	{
		Boolean hasSkeleton = file.getUserData(HAS_SKELETON);
		if(hasSkeleton != null && !hasSkeleton)
		{
			return null;
		}
		VirtualFile moduleVirtualFile = file.getVirtualFile();
		if(moduleVirtualFile != null)
		{
			String moduleName = QualifiedNameFinder.findShortestImportableName(file, moduleVirtualFile);
			if(moduleName != null)
			{
				QualifiedName qName = QualifiedName.fromDottedString(moduleName);
				for(PyCanonicalPathProvider provider : Extensions.getExtensions(PyCanonicalPathProvider.EP_NAME))
				{
					QualifiedName restored = provider.getCanonicalPath(qName, null);
					if(restored != null)
					{
						moduleName = restored.toString();
					}
				}
				PyFile skeletonFile = getUserSkeletonForModuleQName(moduleName, file);
				file.putUserData(HAS_SKELETON, skeletonFile != null);
				return skeletonFile;
			}
		}
		return null;
	}
}
