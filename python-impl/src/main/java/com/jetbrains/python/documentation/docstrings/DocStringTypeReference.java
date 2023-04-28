/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import consulo.language.editor.impl.internal.completion.CompletionUtil;
import consulo.document.util.TextRange;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.StringUtil;
import consulo.language.psi.ElementManipulator;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyImportedModuleType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * User : catherine
 */
public class DocStringTypeReference extends PsiPolyVariantReferenceBase<PsiElement>
{
	@Nullable
	private PyType myType;
	@Nonnull
	private TextRange myFullRange;
	@Nullable
	private final PyImportElement myImportElement;

	public DocStringTypeReference(PsiElement element, TextRange range, @Nonnull TextRange fullRange, @Nullable PyType type, @Nullable PyImportElement importElement)
	{
		super(element, range);
		myFullRange = fullRange;
		myType = type;
		myImportElement = importElement;
	}

	@Nullable
	@Override
	public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException
	{
		if(element.equals(resolve()))
		{
			return element;
		}
		if(myElement instanceof PyStringLiteralExpression && element instanceof PyClass)
		{
			final PyStringLiteralExpression e = (PyStringLiteralExpression) myElement;
			final PyClass cls = (PyClass) element;
			QualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(cls, element);
			if(qname != null)
			{
				qname = qname.append(cls.getName());
				ElementManipulator<PyStringLiteralExpression> manipulator = ElementManipulators.getManipulator(e);
				myType = new PyClassTypeImpl(cls, false);
				return manipulator.handleContentChange(e, myFullRange, qname.toString());
			}
		}
		return null;
	}

	public boolean isSoft()
	{
		return false;
	}

	@Override
	public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException
	{
		newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
		return super.handleElementRename(newElementName);
	}

	@Override
	public boolean isReferenceTo(PsiElement element)
	{
		if(myType instanceof PyImportedModuleType)
		{
			return element.equals(PyUtil.turnInitIntoDir(resolve()));
		}
		return super.isReferenceTo(element);
	}

	@Nonnull
	@Override
	public ResolveResult[] multiResolve(boolean incompleteCode)
	{
		PsiElement result = null;
		final ResolveResultList results = new ResolveResultList();
		if(myType instanceof PyClassType)
		{
			result = ((PyClassType) myType).getPyClass();
		}
		else if(myType instanceof PyImportedModuleType)
		{
			result = ((PyImportedModuleType) myType).getImportedModule().resolve();
		}
		else if(myType instanceof PyModuleType)
		{
			result = ((PyModuleType) myType).getModule();
		}
		if(result != null)
		{
			if(myImportElement != null)
			{
				results.add(new ImportedResolveResult(result, RatedResolveResult.RATE_NORMAL, myImportElement));
			}
			else
			{
				results.poke(result, RatedResolveResult.RATE_NORMAL);
			}
		}
		return results.toArray(ResolveResult.EMPTY_ARRAY);
	}

	@Nonnull
	@Override
	public Object[] getVariants()
	{
		// see PyDocstringCompletionContributor
		return ArrayUtil.EMPTY_OBJECT_ARRAY;
	}

	@Nonnull
	public List<Object> collectTypeVariants()
	{
		final PsiFile file = myElement.getContainingFile();
		final ArrayList<Object> variants = Lists.<Object>newArrayList("str", "int", "basestring", "bool", "buffer", "bytearray", "complex", "dict", "tuple", "enumerate", "file", "float",
				"frozenset", "list", "long", "set", "object");
		if(file instanceof PyFile)
		{
			variants.addAll(((PyFile) file).getTopLevelClasses());
			final List<PyFromImportStatement> fromImports = ((PyFile) file).getFromImports();
			for(PyFromImportStatement fromImportStatement : fromImports)
			{
				final PyImportElement[] elements = fromImportStatement.getImportElements();
				for(PyImportElement element : elements)
				{
					final PyReferenceExpression referenceExpression = element.getImportReferenceExpression();
					if(referenceExpression == null)
					{
						continue;
					}
					final PyType type = TypeEvalContext.userInitiated(file.getProject(), consulo.language.editor.impl.internal.completion.CompletionUtil.getOriginalOrSelf(file)).getType(referenceExpression);
					if(type instanceof PyClassType)
					{
						variants.add(((PyClassType) type).getPyClass());
					}
				}
			}
		}
		return variants;
	}
}
