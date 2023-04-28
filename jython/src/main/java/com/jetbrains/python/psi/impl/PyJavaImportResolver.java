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

package com.jetbrains.python.psi.impl;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.jetbrains.python.psi.resolve.QualifiedNameResolveContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.jython.module.extension.JythonModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;

import javax.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyJavaImportResolver implements PyImportResolver
{
	@Override
	@Nullable
	public PsiElement resolveImportReference(QualifiedName name, QualifiedNameResolveContext context)
	{
		String fqn = name.toString();
		final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(context.getProject());
		if(psiFacade == null)
		{
			return null;
		}
		final PsiJavaPackage aPackage = psiFacade.findPackage(fqn);
		if(aPackage != null)
		{
			return aPackage;
		}

		Module module = context.getModule();
		if(module != null && ModuleUtilCore.getExtension(module, JythonModuleExtension.class) != null)
		{
			final PsiClass aClass = psiFacade.findClass(fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false));
			if(aClass != null)
			{
				return aClass;
			}
		}
		return null;
	}
}
