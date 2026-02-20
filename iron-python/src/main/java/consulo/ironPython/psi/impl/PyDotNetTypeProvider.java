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

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.dotnet.psi.*;
import consulo.dotnet.psi.resolve.DotNetNamespaceAsElement;
import consulo.dotnet.psi.resolve.DotNetTypeRef;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 * @author VISTALL
 */
public class PyDotNetTypeProvider extends PyTypeProviderBase
{
	@Override
	@Nullable
	public PyType getReferenceType(@Nonnull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor)
	{
		if(referenceTarget instanceof DotNetTypeDeclaration)
		{
			return new PyDotNetClassType((DotNetTypeDeclaration) referenceTarget, true);
		}
		if(referenceTarget instanceof DotNetNamespaceAsElement)
		{
			return new PyDotNetNamespaceType((DotNetNamespaceAsElement) referenceTarget, anchor == null ? null : ModuleUtilCore.findModuleForPsiElement(anchor));
		}
		if(referenceTarget instanceof DotNetLikeMethodDeclaration)
		{
			DotNetLikeMethodDeclaration method = (DotNetLikeMethodDeclaration) referenceTarget;
			return new PyDotNetMethodType(method);
		}
		if(referenceTarget instanceof DotNetVariable)
		{
			return asPyType(((DotNetVariable) referenceTarget).toTypeRef(true));
		}
		return null;
	}

	@Nullable
	@RequiredReadAction
	public static PyType asPyType(DotNetTypeRef type)
	{
		PsiElement resolve = type.resolve().getElement();
		if(resolve instanceof DotNetTypeDeclaration)
		{
			return new PyDotNetClassType((DotNetTypeDeclaration) resolve, false);
		}
		return null;
	}

	@Override
	public Ref<PyType> getParameterType(@Nonnull PyNamedParameter param, @Nonnull PyFunction func, @Nonnull TypeEvalContext context)
	{
		if(!(param.getParent() instanceof PyParameterList))
		{
			return null;
		}
		List<PyNamedParameter> params = ParamHelper.collectNamedParameters((PyParameterList) param.getParent());
		int index = params.indexOf(param);
		if(index < 0)
		{
			return null;
		}
		List<PyType> superMethodParameterTypes = new ArrayList<PyType>();
		PySuperMethodsSearch.search(func, context).forEach(psiElement ->
		{
			if(psiElement instanceof DotNetLikeMethodDeclaration)
			{
				DotNetLikeMethodDeclaration method = (DotNetLikeMethodDeclaration) psiElement;
				DotNetParameter[] psiParameters = method.getParameterList().getParameters();
				int javaIndex = method.hasModifier(DotNetModifier.STATIC) ? index : index - 1; // adjust for 'self' parameter
				if(javaIndex < psiParameters.length)
				{
					DotNetTypeRef paramType = psiParameters[javaIndex].toTypeRef(true);
					PsiElement resolve = paramType.resolve().getElement();
					if(resolve instanceof DotNetTypeDeclaration)
					{
						superMethodParameterTypes.add(new PyDotNetClassType((DotNetTypeDeclaration) resolve, false));
					}
				}
			}
			return true;
		});
		if(superMethodParameterTypes.size() > 0)
		{
			return Ref.create(superMethodParameterTypes.get(0));
		}
		return null;
	}
}
