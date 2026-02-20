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
package com.jetbrains.python.impl.psi.impl.references;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyFunctionType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.*;
import consulo.component.extension.Extensions;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class KeywordArgumentCompletionUtil
{
	public static void collectFunctionArgNames(PyElement element, List<LookupElement> ret, @Nonnull TypeEvalContext context)
	{
		PyCallExpression callExpr = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
		if(callExpr != null)
		{
			PyExpression callee = callExpr.getCallee();
			if(callee instanceof PyReferenceExpression && element.getParent() == callExpr.getArgumentList())
			{
				PsiElement def = getElementByType(context, callee);
				if(def == null)
				{
					def = getElementByChain(context, (PyReferenceExpression) callee);
				}

				if(def instanceof PyCallable)
				{
					addKeywordArgumentVariants((PyCallable) def, callExpr, ret);
				}
				else if(def instanceof PyClass)
				{
					PyFunction init = ((PyClass) def).findMethodByName(PyNames.INIT, true, null);  // search in superclasses
					if(init != null)
					{
						addKeywordArgumentVariants(init, callExpr, ret);
					}
				}

				PyType calleeType = context.getType(callee);

				PyUnionType unionType = PyUtil.as(calleeType, PyUnionType.class);
				if(unionType != null)
				{
					fetchCallablesFromUnion(ret, callExpr, unionType, context);
				}

				PyNamedTupleType namedTupleType = PyUtil.as(calleeType, PyNamedTupleType.class);
				if(namedTupleType != null)
				{
					for(String name : namedTupleType.getElementNames())
					{
						ret.add(PyUtil.createNamedParameterLookup(name, element.getProject()));
					}
				}
			}
		}
	}

	@Nullable
	private static PyElement getElementByType(@Nonnull TypeEvalContext context, @Nonnull PyExpression callee)
	{
		PyType pyType = context.getType(callee);
		if(pyType instanceof PyFunctionType)
		{
			return ((PyFunctionType) pyType).getCallable();
		}
		if(pyType instanceof PyClassType)
		{
			return ((PyClassType) pyType).getPyClass();
		}
		return null;
	}

	private static PsiElement getElementByChain(@Nonnull TypeEvalContext context, PyReferenceExpression callee)
	{
		PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
		QualifiedResolveResult result = callee.followAssignmentsChain(resolveContext);
		return result.getElement();
	}

	private static void fetchCallablesFromUnion(@Nonnull List<LookupElement> ret,
			@Nonnull PyCallExpression callExpr,
			@Nonnull PyUnionType unionType,
			@Nonnull TypeEvalContext context)
	{
		for(PyType memberType : unionType.getMembers())
		{
			if(memberType instanceof PyUnionType)
			{
				fetchCallablesFromUnion(ret, callExpr, (PyUnionType) memberType, context);
			}
			if(memberType instanceof PyFunctionType)
			{
				PyFunctionType type = (PyFunctionType) memberType;
				if(type.isCallable())
				{
					addKeywordArgumentVariants(type.getCallable(), callExpr, ret);
				}
			}
			if(memberType instanceof PyCallableType)
			{
				List<PyCallableParameter> callableParameters = ((PyCallableType) memberType).getParameters(context);
				if(callableParameters != null)
				{
					fetchCallablesFromCallableType(ret, callExpr, callableParameters);
				}
			}
		}
	}

	private static void fetchCallablesFromCallableType(@Nonnull List<LookupElement> ret,
			@Nonnull PyCallExpression callExpr,
			@Nonnull Iterable<PyCallableParameter> callableParameters)
	{
		List<String> parameterNames = new ArrayList<>();
		for(PyCallableParameter callableParameter : callableParameters)
		{
			String name = callableParameter.getName();
			if(name != null)
			{
				parameterNames.add(name);
			}
		}
		addKeywordArgumentVariantsForCallable(callExpr, ret, parameterNames);
	}

	public static void addKeywordArgumentVariants(PyCallable callable, PyCallExpression callExpr, List<LookupElement> ret)
	{
		addKeywordArgumentVariants(callable, callExpr, ret, new HashSet<>());
	}

	public static void addKeywordArgumentVariants(PyCallable callable, PyCallExpression callExpr, List<LookupElement> ret, Collection<PyCallable> visited)
	{
		if(visited.contains(callable))
		{
			return;
		}
		visited.add(callable);

		TypeEvalContext context = TypeEvalContext.codeCompletion(callable.getProject(), callable.getContainingFile());

		List<PyParameter> parameters = PyUtil.getParameters(callable, context);
		for(PyParameter parameter : parameters)
		{
			parameter.getName();
		}


		if(callable instanceof PyFunction)
		{
			addKeywordArgumentVariantsForFunction(callExpr, ret, visited, (PyFunction) callable, parameters, context);
		}
		else
		{
			Collection<String> parameterNames = new ArrayList<>();
			for(PyParameter parameter : parameters)
			{
				String name = parameter.getName();
				if(name != null)
				{
					parameterNames.add(name);
				}
			}
			addKeywordArgumentVariantsForCallable(callExpr, ret, parameterNames);
		}
	}

	private static void addKeywordArgumentVariantsForCallable(@Nonnull PyCallExpression callExpr, @Nonnull List<LookupElement> ret, @Nonnull Collection<String> parameterNames)
	{
		for(String parameterName : parameterNames)
		{
			ret.add(PyUtil.createNamedParameterLookup(parameterName, callExpr.getProject()));
		}
	}

	private static void addKeywordArgumentVariantsForFunction(@Nonnull PyCallExpression callExpr,
			@Nonnull List<LookupElement> ret,
			@Nonnull Collection<PyCallable> visited,
			@Nonnull PyFunction function,
			@Nonnull List<PyParameter> parameters,
			@Nonnull TypeEvalContext context)
	{
		boolean needSelf = function.getContainingClass() != null && function.getModifier() != PyFunction.Modifier.STATICMETHOD;
		KwArgParameterCollector collector = new KwArgParameterCollector(needSelf, ret);


		for(PyParameter parameter : parameters)
		{
			parameter.accept(collector);
		}
		if(collector.hasKwArgs())
		{
			for(PyKeywordArgumentProvider provider : Extensions.getExtensions(PyKeywordArgumentProvider.EP_NAME))
			{
				List<String> arguments = provider.getKeywordArguments(function, callExpr);
				for(String argument : arguments)
				{
					ret.add(PyUtil.createNamedParameterLookup(argument, callExpr.getProject()));
				}
			}
			KwArgFromStatementCallCollector fromStatementCallCollector = new KwArgFromStatementCallCollector(ret, collector.getKwArgs());
			PyStatementList statementList = function.getStatementList();
			if(statementList != null)
			{
				statementList.acceptChildren(fromStatementCallCollector);
			}

			//if (collector.hasOnlySelfAndKwArgs()) {
			// nothing interesting besides self and **kwargs, let's look at superclass (PY-778)
			if(fromStatementCallCollector.isKwArgsTransit())
			{

				PsiElement superMethod = PySuperMethodsSearch.search(function, context).findFirst();
				if(superMethod instanceof PyFunction)
				{
					addKeywordArgumentVariants((PyFunction) superMethod, callExpr, ret, visited);
				}
			}
		}
	}

	public static class KwArgParameterCollector extends PyElementVisitor
	{
		private int myCount;
		private final boolean myNeedSelf;
		private final List<LookupElement> myRet;
		private boolean myHasSelf = false;
		private boolean myHasKwArgs = false;
		private PyParameter kwArgsParam = null;

		public KwArgParameterCollector(boolean needSelf, List<LookupElement> ret)
		{
			myNeedSelf = needSelf;
			myRet = ret;
		}

		@Override
		public void visitPyParameter(PyParameter par)
		{
			myCount++;
			if(myCount == 1 && myNeedSelf)
			{
				myHasSelf = true;
				return;
			}
			PyNamedParameter namedParam = par.getAsNamed();
			if(namedParam != null)
			{
				if(!namedParam.isKeywordContainer() && !namedParam.isPositionalContainer())
				{
					LookupElement item = PyUtil.createNamedParameterLookup(namedParam.getName(), par.getProject());
					myRet.add(item);
				}
				else if(namedParam.isKeywordContainer())
				{
					myHasKwArgs = true;
					kwArgsParam = namedParam;
				}
			}
			else
			{
				PyTupleParameter nestedTParam = par.getAsTuple();
				if(nestedTParam != null)
				{
					for(PyParameter inner_par : nestedTParam.getContents())
					{
						inner_par.accept(this);
					}
				}
				// else it's a lone star that can't contribute
			}
		}

		public PyParameter getKwArgs()
		{
			return kwArgsParam;
		}

		public boolean hasKwArgs()
		{
			return myHasKwArgs;
		}

		public boolean hasOnlySelfAndKwArgs()
		{
			return myCount == 2 && myHasSelf && myHasKwArgs;
		}
	}

	public static class KwArgFromStatementCallCollector extends PyElementVisitor
	{
		private final List<LookupElement> myRet;
		private final PyParameter myKwArgs;
		private boolean kwArgsTransit = true;

		public KwArgFromStatementCallCollector(List<LookupElement> ret, @Nonnull PyParameter kwArgs)
		{
			myRet = ret;
			this.myKwArgs = kwArgs;
		}

		@Override
		public void visitPyElement(PyElement node)
		{
			node.acceptChildren(this);
		}

		@Override
		public void visitPySubscriptionExpression(PySubscriptionExpression node)
		{
			String operandName = node.getOperand().getName();
			processGet(operandName, node.getIndexExpression());
		}

		@Override
		public void visitPyCallExpression(PyCallExpression node)
		{
			if(node.isCalleeText("pop", "get", "getattr"))
			{
				PyReferenceExpression child = PsiTreeUtil.getChildOfType(node.getCallee(), PyReferenceExpression.class);
				if(child != null)
				{
					String operandName = child.getName();
					if(node.getArguments().length > 0)
					{
						PyExpression argument = node.getArguments()[0];
						processGet(operandName, argument);
					}
				}
			}
			else if(node.isCalleeText("__init__"))
			{
				kwArgsTransit = false;
				for(PyExpression e : node.getArguments())
				{
					if(e instanceof PyStarArgument)
					{
						PyStarArgument kw = (PyStarArgument) e;
						if(Comparing.equal(myKwArgs.getName(), kw.getFirstChild().getNextSibling().getText()))
						{
							kwArgsTransit = true;
							break;
						}
					}
				}
			}
			super.visitPyCallExpression(node);
		}

		private void processGet(String operandName, PyExpression argument)
		{
			if(Comparing.equal(myKwArgs.getName(), operandName) && argument instanceof PyStringLiteralExpression)
			{
				String name = ((PyStringLiteralExpression) argument).getStringValue();
				if(PyNames.isIdentifier(name))
				{
					myRet.add(PyUtil.createNamedParameterLookup(name, argument.getProject()));
				}
			}
		}

		/**
		 * is name of kwargs parameter the same as transmitted to __init__ call
		 *
		 * @return
		 */
		public boolean isKwArgsTransit()
		{
			return kwArgsTransit;
		}
	}
}
