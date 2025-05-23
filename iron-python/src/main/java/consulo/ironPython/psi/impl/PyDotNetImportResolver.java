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

import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.resolve.QualifiedNameResolveContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.dotnet.psi.DotNetTypeDeclaration;
import consulo.dotnet.psi.resolve.DotNetNamespaceAsElement;
import consulo.dotnet.psi.resolve.DotNetPsiSearcher;
import consulo.ironPython.module.extension.BaseIronPythonModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;

import jakarta.annotation.Nullable;

/**
 * @author yole
 * @author VISTALL
 */
@ExtensionImpl
public class PyDotNetImportResolver implements PyImportResolver
{
	@Override
	@Nullable
	public PsiElement resolveImportReference(QualifiedName name, QualifiedNameResolveContext context)
	{
		String fqn = name.toString();
		final DotNetPsiSearcher psiFacade = DotNetPsiSearcher.getInstance(context.getProject());

		Module module = context.getModule();
		if(module != null && ModuleUtilCore.getExtension(module, BaseIronPythonModuleExtension.class) != null)
		{
			GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
			final DotNetNamespaceAsElement aPackage = psiFacade.findNamespace(fqn, scope);
			if(aPackage != null)
			{
				return aPackage;
			}

			final DotNetTypeDeclaration aClass = psiFacade.findType(fqn, scope);
			if(aClass != null)
			{
				return aClass;
			}
		}
		return null;
	}
}
