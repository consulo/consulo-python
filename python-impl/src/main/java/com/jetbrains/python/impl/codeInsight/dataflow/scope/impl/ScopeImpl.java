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
package com.jetbrains.python.impl.codeInsight.dataflow.scope.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.PyReachingDefsDfaInstance;
import com.jetbrains.python.impl.codeInsight.dataflow.PyReachingDefsSemilattice;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.controlFlow.Instruction;
import consulo.language.dataFlow.DFALimitExceededException;
import consulo.language.dataFlow.map.DFAMap;
import consulo.language.dataFlow.map.DFAMapEngine;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * @author oleg
 */
public class ScopeImpl implements Scope
{
	private volatile Instruction[] myFlow;
	private volatile List<DFAMap<ScopeVariable>> myCachedScopeVariables;
	private volatile Set<String> myGlobals;
	private volatile Set<String> myNonlocals;
	private volatile List<Scope> myNestedScopes;
	private final ScopeOwner myFlowOwner;
	private volatile Map<String, Collection<PsiNamedElement>> myNamedElements;
	private volatile List<PyImportedNameDefiner> myImportedNameDefiners;  // Declarations which declare unknown set of imported names
	private volatile Set<String> myAugAssignments;
	private List<PyTargetExpression> myTargetExpressions;

	public ScopeImpl(ScopeOwner flowOwner)
	{
		myFlowOwner = flowOwner;
	}

	private synchronized void computeFlow()
	{
		if(myFlow == null)
		{
			myFlow = ControlFlowCache.getControlFlow(myFlowOwner).getInstructions();
		}
	}

	public ScopeVariable getDeclaredVariable(@Nonnull PsiElement anchorElement, @Nonnull String name) throws DFALimitExceededException
	{
		computeScopeVariables();
		for(int i = 0; i < myFlow.length; i++)
		{
			Instruction instruction = myFlow[i];
			PsiElement element = instruction.getElement();
			if(element == anchorElement)
			{
				return myCachedScopeVariables.get(i).get(name);
			}
		}
		return null;
	}

	private synchronized void computeScopeVariables() throws DFALimitExceededException
	{
		computeFlow();
		if(myCachedScopeVariables == null)
		{
			PyReachingDefsDfaInstance dfaInstance = new PyReachingDefsDfaInstance();
			PyReachingDefsSemilattice semilattice = new PyReachingDefsSemilattice();
			DFAMapEngine<ScopeVariable> engine = new DFAMapEngine<>(myFlow, dfaInstance, semilattice);
			myCachedScopeVariables = engine.performDFA();
		}
	}

	public boolean isGlobal(String name)
	{
		if(myGlobals == null || myNestedScopes == null)
		{
			collectDeclarations();
		}
		if(myGlobals.contains(name))
		{
			return true;
		}
		for(Scope scope : myNestedScopes)
		{
			if(scope.isGlobal(name))
			{
				return true;
			}
		}
		return false;
	}

	public boolean isNonlocal(String name)
	{
		if(myNonlocals == null || myNestedScopes == null)
		{
			collectDeclarations();
		}
		return myNonlocals.contains(name);
	}

	private boolean isAugAssignment(String name)
	{
		if(myAugAssignments == null || myNestedScopes == null)
		{
			collectDeclarations();
		}
		return myAugAssignments.contains(name);
	}

	public boolean containsDeclaration(String name)
	{
		if(myNamedElements == null || myImportedNameDefiners == null)
		{
			collectDeclarations();
		}
		if(isNonlocal(name))
		{
			return false;
		}
		if(!getNamedElements(name, true).isEmpty())
		{
			return true;
		}
		if(isAugAssignment(name))
		{
			return true;
		}
		for(PyImportedNameDefiner definer : getImportedNameDefiners())
		{
			if(!definer.multiResolveName(name).isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	@Nonnull
	@Override
	public List<PyImportedNameDefiner> getImportedNameDefiners()
	{
		if(myImportedNameDefiners == null)
		{
			collectDeclarations();
		}
		return myImportedNameDefiners;
	}

	@Nonnull
	@Override
	public Collection<PsiNamedElement> getNamedElements(String name, boolean includeNestedGlobals)
	{
		if(myNamedElements == null)
		{
			collectDeclarations();
		}
		if(myNamedElements.containsKey(name))
		{
			Collection<PsiNamedElement> elements = myNamedElements.get(name);
			elements.forEach(PyPsiUtils::assertValid);
			return elements;
		}
		if(includeNestedGlobals && isGlobal(name))
		{
			for(Scope scope : myNestedScopes)
			{
				Collection<PsiNamedElement> globals = scope.getNamedElements(name, true);
				if(!globals.isEmpty())
				{
					globals.forEach(PyPsiUtils::assertValid);
					return globals;
				}
			}
		}
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Collection<PsiNamedElement> getNamedElements()
	{
		if(myNamedElements == null)
		{
			collectDeclarations();
		}
		List<PsiNamedElement> results = Lists.newArrayList();
		for(Collection<PsiNamedElement> elements : myNamedElements.values())
		{
			results.addAll(elements);
		}
		return results;
	}

	@Nonnull
	@Override
	public Collection<PyTargetExpression> getTargetExpressions()
	{
		if(myTargetExpressions == null)
		{
			collectDeclarations();
		}
		return myTargetExpressions;
	}

	private void collectDeclarations()
	{
		final Map<String, Collection<PsiNamedElement>> namedElements = new HashMap<>();
		final List<PyImportedNameDefiner> importedNameDefiners = new ArrayList<>();
		final List<Scope> nestedScopes = new ArrayList<>();
		final Set<String> globals = new HashSet<>();
		final Set<String> nonlocals = new HashSet<>();
		final Set<String> augAssignments = new HashSet<>();
		final List<PyTargetExpression> targetExpressions = new ArrayList<>();
		myFlowOwner.acceptChildren(new PyRecursiveElementVisitor()
		{
			@Override
			public void visitPyTargetExpression(PyTargetExpression node)
			{
				targetExpressions.add(node);
				PsiElement parent = node.getParent();
				if(!node.isQualified() && !(parent instanceof PyImportElement))
				{
					super.visitPyTargetExpression(node);
				}
			}

			@Override
			public void visitPyReferenceExpression(PyReferenceExpression node)
			{
				if(PyAugAssignmentStatementNavigator.getStatementByTarget(node) != null)
				{
					augAssignments.add(node.getName());
				}
				super.visitPyReferenceExpression(node);
			}

			@Override
			public void visitPyGlobalStatement(PyGlobalStatement node)
			{
				for(PyTargetExpression expression : node.getGlobals())
				{
					String name = expression.getReferencedName();
					globals.add(name);
				}
				super.visitPyGlobalStatement(node);
			}

			@Override
			public void visitPyNonlocalStatement(PyNonlocalStatement node)
			{
				for(PyTargetExpression expression : node.getVariables())
				{
					nonlocals.add(expression.getReferencedName());
				}
				super.visitPyNonlocalStatement(node);
			}

			@Override
			public void visitPyFunction(PyFunction node)
			{
				for(PyParameter parameter : node.getParameterList().getParameters())
				{
					PyExpression defaultValue = parameter.getDefaultValue();
					if(defaultValue != null)
					{
						defaultValue.accept(this);
					}
				}
				super.visitPyFunction(node);
			}

			@Override
			public void visitPyElement(PyElement node)
			{
				if(node instanceof PsiNamedElement && !(node instanceof PyKeywordArgument))
				{
					String name = node.getName();
					if(!namedElements.containsKey(name))
					{
						namedElements.put(name, Sets.<PsiNamedElement>newLinkedHashSet());
					}
					namedElements.get(name).add((PsiNamedElement) node);
				}
				if(node instanceof PyImportedNameDefiner)
				{
					importedNameDefiners.add((PyImportedNameDefiner) node);
				}
				if(node instanceof ScopeOwner)
				{
					Scope scope = ControlFlowCache.getScope((ScopeOwner) node);
					nestedScopes.add(scope);
				}
				else
				{
					super.visitPyElement(node);
				}
			}
		});

		myNamedElements = namedElements;
		myImportedNameDefiners = importedNameDefiners;
		myNestedScopes = nestedScopes;
		myGlobals = globals;
		myNonlocals = nonlocals;
		myAugAssignments = augAssignments;
		myTargetExpressions = targetExpressions;
	}
}
