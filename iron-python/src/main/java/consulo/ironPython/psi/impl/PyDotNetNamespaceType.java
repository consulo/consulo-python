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

package consulo.ironPython.psi.impl;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import consulo.dotnet.psi.DotNetTypeDeclaration;
import consulo.dotnet.psi.resolve.DotNetNamespaceAsElement;
import consulo.dotnet.psi.resolve.DotNetPsiSearcher;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ProcessingContext;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyDotNetNamespaceType implements PyType
{
	private final DotNetNamespaceAsElement myNamespaceAsElement;
	@Nullable
	private final Module myModule;

	public PyDotNetNamespaceType(DotNetNamespaceAsElement aNamespaceAsElement, @Nullable Module module)
	{
		myNamespaceAsElement = aNamespaceAsElement;
		myModule = module;
	}

	@Override
	public List<? extends RatedResolveResult> resolveMember(@Nonnull String name,
			@Nullable PyExpression location,
			@Nonnull AccessDirection direction,
			@Nonnull PyResolveContext resolveContext)
	{
		Project project = myNamespaceAsElement.getProject();
		DotNetPsiSearcher facade = DotNetPsiSearcher.getInstance(project);
		String childName = myNamespaceAsElement.getPresentableQName() + "." + name;
		GlobalSearchScope scope = getScope(project);
		ResolveResultList result = new ResolveResultList();
		final DotNetTypeDeclaration[] classes = facade.findTypes(childName, scope);
		for(DotNetTypeDeclaration aClass : classes)
		{
			result.poke(aClass, RatedResolveResult.RATE_NORMAL);
		}
		final DotNetNamespaceAsElement psiPackage = facade.findNamespace(childName, scope);
		if(psiPackage != null)
		{
			result.poke(psiPackage, RatedResolveResult.RATE_NORMAL);
		}
		return result;
	}

	private GlobalSearchScope getScope(Project project)
	{
		return myModule != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule, false) : (GlobalSearchScope) ProjectScopes.getAllScope(project);
	}

	@Override
	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		List<Object> variants = new ArrayList<Object>();
		final GlobalSearchScope scope = getScope(location.getProject());
		Collection<PsiElement> children = myNamespaceAsElement.getChildren(scope, DotNetNamespaceAsElement.ChildrenFilter.NONE);
		for(PsiElement child : children)
		{
			if(child instanceof PsiNamedElement)
			{
				variants.add(LookupElementBuilder.create((PsiNamedElement) child).withIcon(IconDescriptorUpdaters.getIcon(child, 0)));
			}
		}
		return ArrayUtil.toObjectArray(variants);
	}

	@Override
	public String getName()
	{
		return myNamespaceAsElement.getPresentableQName();
	}

	@Override
	public boolean isBuiltin()
	{
		return false;
	}

	@Override
	public void assertValid(String message)
	{
	}
}
