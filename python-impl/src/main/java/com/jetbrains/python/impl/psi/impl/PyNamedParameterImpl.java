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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.function.Processor;
import consulo.component.extension.Extensions;
import consulo.content.scope.SearchScope;
import consulo.ide.impl.idea.util.PlatformIcons;
import consulo.language.ast.ASTNode;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class PyNamedParameterImpl extends PyBaseElementImpl<PyNamedParameterStub> implements PyNamedParameter
{
	public PyNamedParameterImpl(ASTNode astNode)
	{
		super(astNode);
	}

	public PyNamedParameterImpl(PyNamedParameterStub stub)
	{
		this(stub, PyElementTypes.NAMED_PARAMETER);
	}

	public PyNamedParameterImpl(PyNamedParameterStub stub, IStubElementType nodeType)
	{
		super(stub, nodeType);
	}

	@Nullable
	@Override
	public String getName()
	{
		PyNamedParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.getName();
		}
		else
		{
			ASTNode node = getNameIdentifierNode();
			return node != null ? node.getText() : null;
		}
	}

	@Override
	public int getTextOffset()
	{
		ASTNode node = getNameIdentifierNode();
		return node == null ? super.getTextOffset() : node.getTextRange().getStartOffset();
	}

	@Nullable
	protected ASTNode getNameIdentifierNode()
	{
		return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
	}

	public PsiElement getNameIdentifier()
	{
		ASTNode node = getNameIdentifierNode();
		return node == null ? null : node.getPsi();
	}

	public PsiElement setName(@Nonnull String name) throws IncorrectOperationException
	{
		ASTNode oldNameIdentifier = getNameIdentifierNode();
		if(oldNameIdentifier != null)
		{
			ASTNode nameElement = PyUtil.createNewName(this, name);
			getNode().replaceChild(oldNameIdentifier, nameElement);
		}
		return this;
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyNamedParameter(this);
	}

	public boolean isPositionalContainer()
	{
		PyNamedParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.isPositionalContainer();
		}
		else
		{
			return getNode().findChildByType(PyTokenTypes.MULT) != null;
		}
	}

	public boolean isKeywordContainer()
	{
		PyNamedParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.isKeywordContainer();
		}
		else
		{
			return getNode().findChildByType(PyTokenTypes.EXP) != null;
		}
	}

	@Override
	public boolean isKeywordOnly()
	{
		PyParameterList parameters = getStubOrPsiParentOfType(PyParameterList.class);
		if(parameters == null)
		{
			return false;
		}
		boolean varargSeen = false;
		for(PyParameter param : parameters.getParameters())
		{
			if(param == this)
			{
				break;
			}
			PyNamedParameter named = param.getAsNamed();
			if((named != null && named.isPositionalContainer()) || param instanceof PySingleStarParameter)
			{
				varargSeen = true;
				break;
			}
		}
		return varargSeen;
	}

	@Nullable
	public PyExpression getDefaultValue()
	{
		PyNamedParameterStub stub = getStub();
		if(stub != null && !stub.hasDefaultValue())
		{
			return null;
		}
		ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
		if(nodes.length > 0)
		{
			return (PyExpression) nodes[0].getPsi();
		}
		return null;
	}

	public boolean hasDefaultValue()
	{
		PyNamedParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.hasDefaultValue();
		}
		return getDefaultValue() != null;
	}

	@Nonnull
	public String getRepr(boolean includeDefaultValue)
	{
		StringBuilder sb = new StringBuilder();
		if(isPositionalContainer())
		{
			sb.append("*");
		}
		else if(isKeywordContainer())
		{
			sb.append("**");
		}
		sb.append(getName());
		PyExpression defaultValue = getDefaultValue();
		if(includeDefaultValue && defaultValue != null)
		{
			String representation = PyUtil.getReadableRepr(defaultValue, true);
			if(defaultValue instanceof PyStringLiteralExpression)
			{
				Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(defaultValue.getText());
				if(quotes != null)
				{
					representation = quotes.getFirst() + PyStringLiteralUtil.getStringValue(defaultValue) + quotes.getSecond();
				}
			}
			sb.append("=").append(representation);
		}
		return sb.toString();
	}

	@Override
	public PyAnnotation getAnnotation()
	{
		return getStubOrPsiChild(PyElementTypes.ANNOTATION);
	}

	public Icon getIcon(int flags)
	{
		return PlatformIcons.PARAMETER_ICON;
	}

	public PyNamedParameter getAsNamed()
	{
		return this;
	}

	public PyTupleParameter getAsTuple()
	{
		return null; // we're not a tuple
	}

	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		PsiElement parent = getParentByStub();
		if(parent instanceof PyParameterList)
		{
			PyParameterList parameterList = (PyParameterList) parent;
			PyFunction func = parameterList.getContainingFunction();
			if(func != null)
			{
				for(PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME))
				{
					Ref<PyType> resultRef = provider.getParameterType(this, func, context);
					if(resultRef != null)
					{
						return resultRef.get();
					}
				}
				if(isSelf())
				{
					// must be 'self' or 'cls'
					PyClass containingClass = func.getContainingClass();
					if(containingClass != null)
					{
						PyFunction.Modifier modifier = func.getModifier();
						return new PyClassTypeImpl(containingClass, modifier == PyFunction.Modifier.CLASSMETHOD);
					}
				}
				if(isKeywordContainer())
				{
					return PyBuiltinCache.getInstance(this).getDictType();
				}
				if(isPositionalContainer())
				{
					return PyBuiltinCache.getInstance(this).getTupleType();
				}
				if(context.maySwitchToAST(this))
				{
					PyExpression defaultValue = getDefaultValue();
					if(defaultValue != null)
					{
						PyType type = context.getType(defaultValue);
						if(type != null && !(type instanceof PyNoneType))
						{
							if(type instanceof PyTupleType)
							{
								return PyUnionType.createWeakType(type);
							}
							return type;
						}
					}
				}
				// Guess the type from file-local calls
				if(context.allowCallContext(this))
				{
					List<PyType> types = new ArrayList<>();
					processLocalCalls(func, call -> {
						PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
						PyArgumentList argumentList = call.getArgumentList();
						if(argumentList != null)
						{
							PyCallExpression.PyArgumentsMapping mapping = call.mapArguments(resolveContext);
							for(Map.Entry<PyExpression, PyNamedParameter> entry : mapping.getMappedParameters().entrySet())
							{
								if(entry.getValue() == this)
								{
									PyExpression argument = entry.getKey();
									if(argument != null)
									{
										PyType type = context.getType(argument);
										if(type != null)
										{
											types.add(type);
											return true;
										}
									}
								}
							}
						}
						return true;
					});
					if(!types.isEmpty())
					{
						return PyUnionType.createWeakType(PyUnionType.union(types));
					}
				}
				if(context.maySwitchToAST(this))
				{
					Set<String> attributes = collectUsedAttributes(context);
					if(!attributes.isEmpty())
					{
						return new PyStructuralType(attributes, true);
					}
				}
			}
		}
		return null;
	}

	@Override
	public ItemPresentation getPresentation()
	{
		return new PyElementPresentation(this);
	}

	@Nonnull
	private Set<String> collectUsedAttributes(@Nonnull final TypeEvalContext context)
	{
		final Set<String> result = new LinkedHashSet<>();
		final ScopeOwner owner = ScopeUtil.getScopeOwner(this);
		String name = getName();
		if(owner != null && name != null)
		{
			owner.accept(new PyRecursiveElementVisitor()
			{
				@Override
				public void visitPyElement(PyElement node)
				{
					if(node instanceof ScopeOwner && node != owner)
					{
						return;
					}
					if(node instanceof PyQualifiedExpression)
					{
						PyQualifiedExpression expr = (PyQualifiedExpression) node;
						PyExpression qualifier = expr.getQualifier();
						if(qualifier != null)
						{
							String attributeName = expr.getReferencedName();
							PyExpression referencedExpr = node instanceof PyBinaryExpression && PyNames.isRightOperatorName(attributeName) ? ((PyBinaryExpression) node).getRightExpression() :
									qualifier;
							if(referencedExpr != null)
							{
								PsiReference ref = referencedExpr.getReference();
								if(ref != null && ref.isReferenceTo(PyNamedParameterImpl.this))
								{
									if(attributeName != null && !result.contains(attributeName))
									{
										result.add(attributeName);
									}
								}
							}
						}
						else
						{
							PsiReference ref = expr.getReference();
							if(ref != null && ref.isReferenceTo(PyNamedParameterImpl.this))
							{
								PyNamedParameter parameter = getParameterByCallArgument(expr, context);
								if(parameter != null)
								{
									PyType type = context.getType(parameter);
									if(type instanceof PyStructuralType)
									{
										result.addAll(((PyStructuralType) type).getAttributeNames());
									}
								}
							}
						}
					}
					super.visitPyElement(node);
				}

				@Override
				public void visitPyIfStatement(PyIfStatement node)
				{
					PyExpression ifCondition = node.getIfPart().getCondition();
					if(ifCondition != null)
					{
						ifCondition.accept(this);
					}
					for(PyIfPart part : node.getElifParts())
					{
						PyExpression elseIfCondition = part.getCondition();
						if(elseIfCondition != null)
						{
							elseIfCondition.accept(this);
						}
					}
				}

				@Override
				public void visitPyCallExpression(PyCallExpression node)
				{
					Optional.ofNullable(node.getCallee()).filter(callee -> "len".equals(callee.getName())).map(PyExpression::getReference).map(PsiReference::resolve).filter(element -> PyBuiltinCache
							.getInstance(element).isBuiltin(element)).ifPresent(callable -> {
						PyReferenceExpression argument = node.getArgument(0, PyReferenceExpression.class);
						if(argument != null && argument.getReference().isReferenceTo(PyNamedParameterImpl.this))
						{
							result.add(PyNames.LEN);
						}
					});

					super.visitPyCallExpression(node);
				}

				@Override
				public void visitPyForStatement(PyForStatement node)
				{
					Optional.of(node.getForPart()).map(PyForPart::getSource).map(PyExpression::getReference).filter(reference -> reference.isReferenceTo(PyNamedParameterImpl.this)).ifPresent
							(reference -> result.add(PyNames.ITER));

					super.visitPyForStatement(node);
				}
			});
		}
		return result;
	}

	@Nullable
	private PyNamedParameter getParameterByCallArgument(@Nonnull PsiElement element, @Nonnull TypeEvalContext context)
	{
		PyArgumentList argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
		if(argumentList != null)
		{
			boolean elementIsArgument = false;
			for(PyExpression argument : argumentList.getArgumentExpressions())
			{
				if(PyPsiUtils.flattenParens(argument) == element)
				{
					elementIsArgument = true;
					break;
				}
			}
			PyCallExpression callExpression = argumentList.getCallExpression();
			if(elementIsArgument && callExpression != null)
			{
				PyExpression callee = callExpression.getCallee();
				if(callee instanceof PyReferenceExpression)
				{
					PyReferenceExpression calleeReferenceExpr = (PyReferenceExpression) callee;
					PyExpression firstQualifier = PyPsiUtils.getFirstQualifier(calleeReferenceExpr);
					if(firstQualifier != null)
					{
						PsiReference ref = firstQualifier.getReference();
						if(ref != null && ref.isReferenceTo(this))
						{
							return null;
						}
					}
				}
				PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
				PyCallExpression.PyArgumentsMapping mapping = callExpression.mapArguments(resolveContext);
				for(Map.Entry<PyExpression, PyNamedParameter> entry : mapping.getMappedParameters().entrySet())
				{
					if(entry.getKey() == element)
					{
						return entry.getValue();
					}
				}
			}
		}
		return null;
	}

	private static void processLocalCalls(@Nonnull PyFunction function, @Nonnull Processor<PyCallExpression> processor)
	{
		PsiFile file = function.getContainingFile();
		String name = function.getName();
		if(file != null && name != null)
		{
			// Text search is faster than ReferencesSearch in LocalSearchScope
			String text = file.getText();
			for(int pos = text.indexOf(name); pos != -1; pos = text.indexOf(name, pos + 1))
			{
				PsiReference ref = file.findReferenceAt(pos);
				if(ref != null && ref.isReferenceTo(function))
				{
					PyCallExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(pos), PyCallExpression.class);
					if(expr != null && !processor.process(expr))
					{
						return;
					}
				}
			}
		}
	}

	@Override
	public String toString()
	{
		return super.toString() + "('" + getName() + "')";
	}

	@Nonnull
	@Override
	public SearchScope getUseScope()
	{
		ScopeOwner owner = ScopeUtil.getScopeOwner(this);
		if(owner instanceof PyFunction)
		{
			return new LocalSearchScope(owner);
		}
		return new LocalSearchScope(getContainingFile());
	}

	@Override
	public boolean isSelf()
	{
		if(isPositionalContainer() || isKeywordContainer())
		{
			return false;
		}
		PyFunction function = getStubOrPsiParentOfType(PyFunction.class);
		if(function == null)
		{
			return false;
		}
		PyClass cls = function.getContainingClass();
		PyParameter[] parameters = function.getParameterList().getParameters();
		if(cls != null && parameters.length > 0 && parameters[0] == this)
		{
			if(PyNames.NEW.equals(function.getName()))
			{
				return true;
			}
			PyFunction.Modifier modifier = function.getModifier();
			if(modifier != PyFunction.Modifier.STATICMETHOD)
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	@Override
	public PsiComment getTypeComment()
	{
		for(PsiElement next = getNextSibling(); next != null; next = next.getNextSibling())
		{
			if(next.textContains('\n'))
			{
				break;
			}
			if(!(next instanceof PsiWhiteSpace))
			{
				if(",".equals(next.getText()))
				{
					continue;
				}
				if(next instanceof PsiComment && PyTypingTypeProvider.getTypeCommentValue(next.getText()) != null)
				{
					return (PsiComment) next;
				}
				break;
			}
		}
		return null;
	}

	@Nullable
	@Override
	public String getTypeCommentAnnotation()
	{
		PyNamedParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.getTypeComment();
		}
		PsiComment comment = getTypeComment();
		if(comment != null)
		{
			return PyTypingTypeProvider.getTypeCommentValue(comment.getText());
		}
		return null;
	}
}
