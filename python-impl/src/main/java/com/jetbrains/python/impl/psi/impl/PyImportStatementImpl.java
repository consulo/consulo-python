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
package com.jetbrains.python.impl.psi.impl;

import com.google.common.collect.ImmutableList;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyImportStatementImpl extends PyBaseElementImpl<PyImportStatementStub> implements PyImportStatement
{
	public PyImportStatementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	public PyImportStatementImpl(PyImportStatementStub stub)
	{
		this(stub, PyElementTypes.IMPORT_STATEMENT);
	}

	public PyImportStatementImpl(PyImportStatementStub stub, IStubElementType nodeType)
	{
		super(stub, nodeType);
	}

	@Nonnull
	public PyImportElement[] getImportElements()
	{
		PyImportStatementStub stub = getStub();
		if(stub != null)
		{
			return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, count -> new PyImportElement[count]);
		}
		return childrenToPsi(TokenSet.create(PyElementTypes.IMPORT_ELEMENT), new PyImportElement[0]);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyImportStatement(this);
	}

	@Override
	public void deleteChildInternal(@Nonnull ASTNode child)
	{
		if(ArrayUtil.contains(child.getPsi(), getImportElements()))
		{
			PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
		}
		super.deleteChildInternal(child);
	}

	@Nonnull
	@Override
	public List<String> getFullyQualifiedObjectNames()
	{
		return getImportElementNames(getImportElements());
	}

	/**
	 * Returns list of qualified names of import elements filtering out nulls
	 *
	 * @param elements import elements
	 * @return list of qualified names
	 */
	@Nonnull
	public static List<String> getImportElementNames(@Nonnull PyImportElement... elements)
	{
		List<String> result = new ArrayList<>(elements.length);
		for(PyImportElement element : elements)
		{
			QualifiedName qName = element.getImportedQName();
			if(qName != null)
			{
				result.add(qName.toString());
			}
		}
		return result;
	}

	@Nonnull
	@Override
	public Iterable<PyElement> iterateNames()
	{
		PyElement resolved = as(resolveImplicitSubModule(), PyElement.class);
		return resolved != null ? ImmutableList.<PyElement>of(resolved) : Collections.<PyElement>emptyList();
	}

	@Nonnull
	@Override
	public List<RatedResolveResult> multiResolveName(@Nonnull String name)
	{
		PyImportElement[] elements = getImportElements();
		if(elements.length == 1)
		{
			PyImportElement element = elements[0];
			QualifiedName importedQName = element.getImportedQName();
			if(importedQName != null && importedQName.getComponentCount() > 1 && name.equals(importedQName.getLastComponent()))
			{
				return ResolveResultList.to(resolveImplicitSubModule());
			}
		}
		return Collections.emptyList();
	}

	/**
	 * The statement 'import pkg1.m1' makes 'm1' available as a local name in the package 'pkg1'.
	 * <p>
	 * http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
	 */
	@Nullable
	private PsiElement resolveImplicitSubModule()
	{
		PyImportElement[] elements = getImportElements();
		if(elements.length == 1)
		{
			PyImportElement element = elements[0];
			QualifiedName importedQName = element.getImportedQName();
			PsiFile file = element.getContainingFile();
			if(file != null)
			{
				if(importedQName != null && importedQName.getComponentCount() > 1 && PyUtil.isPackage(file))
				{
					QualifiedName packageQName = importedQName.removeLastComponent();
					PsiElement resolvedImport = PyUtil.turnDirIntoInit(ResolveImportUtil.resolveImportElement(element, packageQName));
					if(resolvedImport == file)
					{
						return element.resolve();
					}
				}
			}
		}
		return null;
	}
}
