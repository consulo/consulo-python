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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.StandardTokenTypes;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyFromImportStatementImpl extends PyBaseElementImpl<PyFromImportStatementStub> implements PyFromImportStatement
{
	public PyFromImportStatementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	public PyFromImportStatementImpl(PyFromImportStatementStub stub)
	{
		this(stub, PyElementTypes.FROM_IMPORT_STATEMENT);
	}

	public PyFromImportStatementImpl(PyFromImportStatementStub stub, IStubElementType nodeType)
	{
		super(stub, nodeType);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyFromImportStatement(this);
	}

	@Override
    @RequiredReadAction
    public boolean isStarImport()
	{
		PyFromImportStatementStub stub = getStub();
		if(stub != null)
		{
			return stub.isStarImport();
		}
		return getStarImportElement() != null;
	}

	@Nullable
    @Override
    @RequiredReadAction
	public PyReferenceExpression getImportSource()
	{
		return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getReferenceExpressionTokens(), 0);
	}

	@Override
    @RequiredReadAction
    public QualifiedName getImportSourceQName()
	{
		PyFromImportStatementStub stub = getStub();
		if(stub != null)
		{
			QualifiedName qName = stub.getImportSourceQName();
			if(qName != null && qName.getComponentCount() == 0)
			{  // relative import only: from .. import the_name
				return null;
			}
			return qName;
		}

		PyReferenceExpression importSource = getImportSource();
		if(importSource == null)
		{
			return null;
		}
		return importSource.asQualifiedName();
	}

	@Override
    @RequiredReadAction
    public PyImportElement[] getImportElements()
	{
		PyFromImportStatementStub stub = getStub();
		if(stub != null)
		{
			return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, PyImportElement[]::new);
		}
		List<PyImportElement> result = new ArrayList<>();
		ASTNode importKeyword = getNode().findChildByType(PyTokenTypes.IMPORT_KEYWORD);
		if(importKeyword != null)
		{
			for(ASTNode node = importKeyword.getTreeNext(); node != null; node = node.getTreeNext())
			{
				if(node.getElementType() == PyElementTypes.IMPORT_ELEMENT)
				{
					result.add((PyImportElement) node.getPsi());
				}
			}
		}
		return result.toArray(new PyImportElement[result.size()]);
	}

	@Nullable
    @Override
    @RequiredReadAction
	public PyStarImportElement getStarImportElement()
	{
		return getStubOrPsiChild(PyElementTypes.STAR_IMPORT_ELEMENT);
	}

	@Override
    @RequiredReadAction
    public int getRelativeLevel()
	{
		PyFromImportStatementStub stub = getStub();
		if(stub != null)
		{
			return stub.getRelativeLevel();
		}

		int result = 0;
		ASTNode seeker = getNode().getFirstChildNode();
		while(seeker != null && (seeker.getElementType() == PyTokenTypes.FROM_KEYWORD || seeker.getElementType() == StandardTokenTypes.WHITE_SPACE))
		{
			seeker = seeker.getTreeNext();
		}
		while(seeker != null && seeker.getElementType() == PyTokenTypes.DOT)
		{
			result++;
			seeker = seeker.getTreeNext();
		}
		return result;
	}

	@Override
    @RequiredReadAction
    public boolean isFromFuture()
	{
		QualifiedName qName = getImportSourceQName();
		return qName != null && qName.matches(PyNames.FUTURE_MODULE);
	}

	@Override
    @RequiredReadAction
    public PsiElement getLeftParen()
	{
		return findChildByType(PyTokenTypes.LPAR);
	}

	@Override
    @RequiredReadAction
	public PsiElement getRightParen()
	{
		return findChildByType(PyTokenTypes.RPAR);
	}

	@Override
    @RequiredReadAction
    public boolean processDeclarations(PsiScopeProcessor processor, ResolveState state, PsiElement lastParent, PsiElement place)
	{
		// import is per-file
		if(place.getContainingFile() != getContainingFile())
		{
			return true;
		}
		if(isStarImport())
		{
			List<PsiElement> targets = ResolveImportUtil.resolveFromImportStatementSource(this, getImportSourceQName());
			for(PsiElement target : targets)
			{
				PsiElement importedFile = PyUtil.turnDirIntoInit(target);
				if(importedFile != null)
				{
					if(!importedFile.processDeclarations(processor, state, null, place))
					{
						return false;
					}
				}
			}
		}
		else
		{
			PyImportElement[] importElements = getImportElements();
			for(PyImportElement element : importElements)
			{
				if(!processor.execute(element, state))
				{
					return false;
				}
			}
		}
		return true;
	}

    @Override
    @RequiredWriteAction
	public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before)
	{
		if(anchor == null)
		{
			// adding last element; the import may be "from ... import (...)", must get before the last ")"
			PsiElement lastChild = getLastChild();
			if(lastChild != null)
			{
				while(lastChild instanceof PsiComment)
				{
					lastChild = lastChild.getPrevSibling();
					anchor = lastChild.getNode();
				}
				ASTNode rParNode = lastChild.getNode();
				if(rParNode != null && rParNode.getElementType() == PyTokenTypes.RPAR)
				{
					anchor = rParNode;
				}
			}
		}
		ASTNode result = super.addInternal(first, last, anchor, before);
		ASTNode prevNode = result;
		do
		{
			prevNode = prevNode.getTreePrev();
		}
		while(prevNode != null && prevNode.getElementType() == StandardTokenTypes.WHITE_SPACE);

		if(prevNode != null && prevNode.getElementType() == PyElementTypes.IMPORT_ELEMENT &&
				result.getElementType() == PyElementTypes.IMPORT_ELEMENT)
		{
			ASTNode comma = PyElementGenerator.getInstance(getProject()).createComma();
			super.addInternal(comma, comma, prevNode, false);
		}

		return result;
	}

	@Override
    @RequiredWriteAction
	public void deleteChildInternal(ASTNode child)
	{
		if(ArrayUtil.contains(child.getPsi(), getImportElements()))
		{
			PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
		}
		super.deleteChildInternal(child);
	}

	@Nullable
    @Override
    @RequiredReadAction
	public PsiFileSystemItem resolveImportSource()
	{
		return as(ContainerUtil.getFirstItem(resolveImportSourceCandidates()), PsiFileSystemItem.class);
	}

	@Override
    @RequiredReadAction
	public List<PsiElement> resolveImportSourceCandidates()
	{
		QualifiedName qName = getImportSourceQName();
		if(qName == null)
		{
			int level = getRelativeLevel();
			if(level > 0)
			{
				PsiDirectory upper = ResolveImportUtil.stepBackFrom(getContainingFile().getOriginalFile(), level);
				return upper == null ? Collections.<PsiElement>emptyList() : Collections.<PsiElement>singletonList(upper);
			}
		}
		return ResolveImportUtil.resolveFromImportStatementSource(this, qName);
	}

	@Override
    @RequiredReadAction
	public List<String> getFullyQualifiedObjectNames()
	{
		QualifiedName source = getImportSourceQName();

		String prefix = (source != null) ? (source.join(".") + '.') : "";

		List<String> unqualifiedNames = PyImportStatementImpl.getImportElementNames(getImportElements());

		List<String> result = new ArrayList<>(unqualifiedNames.size());

		for(String unqualifiedName : unqualifiedNames)
		{
			result.add(prefix + unqualifiedName);
		}
		return result;
	}

	@Override
    @RequiredReadAction
	public Iterable<PyElement> iterateNames()
	{
		PyElement resolved = as(resolveImplicitSubModule(), PyElement.class);
		return resolved != null ? ImmutableList.<PyElement>of(resolved) : Collections.<PyElement>emptyList();
	}

	@Override
    @RequiredReadAction
	public List<RatedResolveResult> multiResolveName(String name)
	{
		QualifiedName importSourceQName = getImportSourceQName();
		if(importSourceQName != null && importSourceQName.endsWith(name))
		{
			PsiElement element = resolveImplicitSubModule();
			if(element != null)
			{
				return ResolveResultList.to(element);
			}
		}
		return Collections.emptyList();
	}

	/**
	 * The statement 'from pkg1.m1 import ...' makes 'm1' available as a local name in the package 'pkg1'.
	 * <p>
	 * http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
	 */
	@Nullable
    @RequiredReadAction
	private PsiElement resolveImplicitSubModule()
	{
		QualifiedName importSourceQName = getImportSourceQName();
		if(importSourceQName != null)
		{
			String name = importSourceQName.getLastComponent();
			PsiFile file = getContainingFile();
			if(name != null && PyUtil.isPackage(file))
			{
				PsiElement resolvedImportSource = PyUtil.turnInitIntoDir(resolveImportSource());
				if(resolvedImportSource != null && resolvedImportSource.getParent() == file.getContainingDirectory())
				{
					return resolvedImportSource;
				}
			}
		}
		return null;
	}
}
