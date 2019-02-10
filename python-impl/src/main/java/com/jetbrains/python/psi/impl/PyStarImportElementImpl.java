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
package com.jetbrains.python.psi.impl;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.toolbox.ChainIterable;
import consulo.ui.image.Image;

/**
 * @author dcheryasov
 */
public class PyStarImportElementImpl extends PyBaseElementImpl<PyStarImportElementStub> implements PyStarImportElement
{
	public PyStarImportElementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	public PyStarImportElementImpl(final PyStarImportElementStub stub)
	{
		super(stub, PyElementTypes.STAR_IMPORT_ELEMENT);
	}

	@Nonnull
	public Iterable<PyElement> iterateNames()
	{
		if(getParent() instanceof PyFromImportStatement)
		{
			PyFromImportStatement fromImportStatement = (PyFromImportStatement) getParent();
			final List<PsiElement> importedFiles = fromImportStatement.resolveImportSourceCandidates();
			ChainIterable<PyElement> chain = new ChainIterable<>();
			for(PsiElement importedFile : new HashSet<>(importedFiles))
			{ // resolver gives lots of duplicates
				final PsiElement source = PyUtil.turnDirIntoInit(importedFile);
				if(source instanceof PyFile)
				{
					final PyFile sourceFile = (PyFile) source;
					chain.add(filterStarImportableNames(sourceFile.iterateNames(), sourceFile));
				}
			}
			return chain;
		}
		return Collections.emptyList();
	}

	@Nonnull
	private static Iterable<PyElement> filterStarImportableNames(@Nonnull Iterable<PyElement> declaredNames, @Nonnull final PyFile file)
	{
		return Iterables.filter(declaredNames, new Predicate<PyElement>()
		{
			@Override
			public boolean apply(@Nullable PyElement input)
			{
				final String name = input != null ? input.getName() : null;
				return name != null && PyUtil.isStarImportableFrom(name, file);
			}
		});
	}

	@Nonnull
	public List<RatedResolveResult> multiResolveName(@Nonnull String name)
	{
		return PyUtil.getParameterizedCachedValue(this, name, this::calculateMultiResolveName);
	}

	@Nonnull
	private List<RatedResolveResult> calculateMultiResolveName(@Nonnull String name)
	{
		if(PyUtil.isClassPrivateName(name))
		{
			return Collections.emptyList();
		}
		final PsiElement parent = getParentByStub();
		if(parent instanceof PyFromImportStatement)
		{
			PyFromImportStatement fromImportStatement = (PyFromImportStatement) parent;
			final List<PsiElement> importedFiles = fromImportStatement.resolveImportSourceCandidates();
			for(PsiElement importedFile : new HashSet<>(importedFiles))
			{ // resolver gives lots of duplicates
				final PsiElement source = PyUtil.turnDirIntoInit(importedFile);
				if(source instanceof PyFile)
				{
					PyFile sourceFile = (PyFile) source;
					final PyModuleType moduleType = new PyModuleType(sourceFile);
					final List<? extends RatedResolveResult> results = moduleType.resolveMember(name, null, AccessDirection.READ, PyResolveContext.defaultContext());
					if(results != null && !results.isEmpty() && PyUtil.isStarImportableFrom(name, sourceFile))
					{
						if(results.isEmpty())
						{
							return Collections.emptyList();
						}
						final List<RatedResolveResult> res = Lists.newArrayList();
						for(RatedResolveResult result : results)
						{
							res.add(result);
						}
						return res;
					}
				}
			}
		}
		return Collections.emptyList();
	}

	@Override
	public ItemPresentation getPresentation()
	{
		return new ItemPresentation()
		{

			private String getName()
			{
				PyFromImportStatement elt = PsiTreeUtil.getParentOfType(PyStarImportElementImpl.this, PyFromImportStatement.class);
				if(elt != null)
				{ // always? who knows :)
					PyReferenceExpression imp_src = elt.getImportSource();
					if(imp_src != null)
					{
						return PyPsiUtils.toPath(imp_src);
					}
				}
				return "<?>";
			}

			public String getPresentableText()
			{
				return getName();
			}

			public String getLocationString()
			{
				return "| " + "from " + getName() + " import *";
			}

			public Image getIcon()
			{
				return null;
			}
		};
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyStarImportElement(this);
	}
}
