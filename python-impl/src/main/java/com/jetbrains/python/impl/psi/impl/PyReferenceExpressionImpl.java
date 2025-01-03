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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.console.PydevConsoleRunner;
import com.jetbrains.python.impl.console.completion.PydevConsoleReference;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.references.PyImportReference;
import com.jetbrains.python.impl.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.impl.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.impl.refactoring.PyDefUseUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.component.extension.ExtensionException;
import consulo.component.extension.Extensions;
import consulo.language.ast.ASTNode;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.logging.Logger;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * Implements reference expression PSI.
 *
 * @author yole
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression
{
	private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.psi.impl.PyReferenceExpressionImpl");
	private QualifiedName myQualifiedName = null;

	public PyReferenceExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Nonnull
	@Override
	public PsiPolyVariantReference getReference()
	{
		//noinspection InstanceofIncompatibleInterface
		assert !(this instanceof StubBasedPsiElement);
		final TypeEvalContext context = TypeEvalContext.codeAnalysis(getProject(), getContainingFile());
		return getReference(PyResolveContext.defaultContext().withTypeEvalContext(context));
	}

	@Nonnull
	@Override
	public PsiPolyVariantReference getReference(PyResolveContext context)
	{
		final PsiFile file = getContainingFile();
		final PyExpression qualifier = getQualifier();

		// Handle import reference
		final PsiElement importParent = PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class);
		if(importParent != null)
		{
			return PyImportReference.forElement(this, importParent, context);
		}

		// Return special reference
		final ConsoleCommunication communication = file.getCopyableUserData(PydevConsoleRunner.CONSOLE_KEY);
		if(communication != null)
		{
			if(qualifier != null)
			{
				return new PydevConsoleReference(this, communication, qualifier.getText() + ".", context.allowRemote());
			}
			return new PydevConsoleReference(this, communication, "", context.allowRemote());
		}

		if(qualifier != null)
		{
			return new PyQualifiedReference(this, context);
		}

		return new PyReferenceImpl(this, context);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyReferenceExpression(this);
	}

	@Nullable
	public PyExpression getQualifier()
	{
		final ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
		return (PyExpression) (nodes.length == 1 ? nodes[0].getPsi() : null);
	}

	@Override
	public boolean isQualified()
	{
		return getQualifier() != null;
	}

	@Nullable
	public String getReferencedName()
	{
		final ASTNode nameElement = getNameElement();
		return nameElement != null ? nameElement.getText() : null;
	}

	@Nullable
	public ASTNode getNameElement()
	{
		return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
	}

	@Nullable
	@Override
	public String getName()
	{
		return getReferencedName();
	}


	private static final QualifiedResolveResult EMPTY_RESULT = new QualifiedResolveResultEmpty();

	@Nonnull
	public QualifiedResolveResult followAssignmentsChain(PyResolveContext resolveContext)
	{
		PyReferenceExpression seeker = this;
		QualifiedResolveResult ret = null;
		List<PyExpression> qualifiers = new ArrayList<>();
		PyExpression qualifier = seeker.getQualifier();
		if(qualifier != null)
		{
			qualifiers.add(qualifier);
		}
		Set<PsiElement> visited = new HashSet<>();
		visited.add(this);
		SEARCH:
		while(ret == null)
		{
			ResolveResult[] targets = seeker.getReference(resolveContext).multiResolve(false);
			for(ResolveResult target : targets)
			{
				PsiElement elt = target.getElement();
				if(elt instanceof PyTargetExpression)
				{
					final PyTargetExpression expr = (PyTargetExpression) elt;
					final TypeEvalContext context = resolveContext.getTypeEvalContext();
					final PsiElement assigned_from;
					if(context.maySwitchToAST(expr))
					{
						assigned_from = expr.findAssignedValue();
					}
					else
					{
						assigned_from = expr.resolveAssignedValue(resolveContext);
					}
					if(assigned_from instanceof PyReferenceExpression)
					{
						if(visited.contains(assigned_from))
						{
							break;
						}
						visited.add(assigned_from);
						seeker = (PyReferenceExpression) assigned_from;
						if(seeker.getQualifier() != null)
						{
							qualifiers.add(seeker.getQualifier());
						}
						continue SEARCH;
					}
					else if(assigned_from != null)
					{
						ret = new QualifiedResolveResultImpl(assigned_from, qualifiers, false);
					}
				}
				else if(ret == null && elt instanceof PyElement && target.isValidResult())
				{
					// remember this result, but a further reference may be the next resolve result
					ret = new QualifiedResolveResultImpl(elt, qualifiers, target instanceof ImplicitResolveResult);
				}
			}
			// all resolve results checked, reassignment not detected, nothing more to do
			break;
		}
		if(ret == null)
		{
			ret = EMPTY_RESULT;
		}
		return ret;
	}

	@Nullable
	public QualifiedName asQualifiedName()
	{
		if(myQualifiedName == null)
		{
			myQualifiedName = PyPsiUtils.asQualifiedName(this);
		}
		return myQualifiedName;
	}

	@Override
	public String toString()
	{
		return "PyReferenceExpression: " + getReferencedName();
	}

	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		if(!TypeEvalStack.mayEvaluate(this))
		{
			return null;
		}
		try
		{
			final boolean qualified = isQualified();
			if(!qualified)
			{
				String name = getReferencedName();
				if(PyNames.NONE.equals(name))
				{
					return PyNoneType.INSTANCE;
				}
			}
			final PyType providedType = getTypeFromProviders(context);
			if(providedType != null)
			{
				return providedType;
			}
			if(qualified)
			{
				if(!context.maySwitchToAST(this))
				{
					return null;
				}
				PyType maybe_type = PyUtil.getSpecialAttributeType(this, context);
				if(maybe_type != null)
				{
					return maybe_type;
				}
				Ref<PyType> typeOfProperty = getTypeOfProperty(context);
				if(typeOfProperty != null)
				{
					return typeOfProperty.get();
				}
				final PyType typeByControlFlow = getQualifiedReferenceTypeByControlFlow(context);
				if(typeByControlFlow != null)
				{
					return typeByControlFlow;
				}
			}
			final PsiPolyVariantReference reference = getReference(PyResolveContext.noImplicits().withTypeEvalContext(context));
			final List<PsiElement> targets = PyUtil.multiResolveTopPriority(reference);

			final List<PyType> members = new ArrayList<>();
			for(PsiElement target : targets)
			{
				if(target == this || target == null)
				{
					continue;
				}
				if(!target.isValid())
				{
		  /*LOG.error("Reference " + this + " resolved to invalid element " + target + " (text=" + target.getText() + ")");
          continue;*/
					throw new PsiInvalidElementAccessException(this);
				}
				members.add(getTypeFromTarget(target, context, this));
			}
			final PyType type = PyUnionType.union(members);
			if(qualified && type instanceof PyNoneType)
			{
				return null;
			}
			return type;
		}
		finally
		{
			TypeEvalStack.evaluated(this);
		}
	}

	@Nullable
	private PyType getQualifiedReferenceTypeByControlFlow(@Nonnull TypeEvalContext context)
	{
		PyExpression qualifier = getQualifier();
		if(context.allowDataFlow(this) && qualifier != null)
		{
			PyExpression next = qualifier;
			while(next != null)
			{
				qualifier = next;
				next = qualifier instanceof PyQualifiedExpression ? ((PyQualifiedExpression) qualifier).getQualifier() : null;
			}
			final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(this);
			final QualifiedName qname = asQualifiedName();
			if(qname != null && scopeOwner != null)
			{
				return getTypeByControlFlow(qname.toString(), context, qualifier, scopeOwner);
			}
		}
		return null;
	}

	@Nullable
	private Ref<PyType> getTypeOfProperty(@Nonnull TypeEvalContext context)
	{
		final PyExpression qualifier = getQualifier();
		final String name = getName();
		if(name != null && qualifier != null)
		{
			final PyType qualifierType = context.getType(qualifier);
			return getTypeOfProperty(qualifierType, name, context);
		}
		return null;
	}

	@Nullable
	private Ref<PyType> getTypeOfProperty(@Nullable PyType qualifierType, @Nonnull String name, @Nonnull TypeEvalContext context)
	{
		if(qualifierType instanceof PyClassType)
		{
			final PyClassType classType = (PyClassType) qualifierType;
			PyClass pyClass = classType.getPyClass();
			Property property = pyClass.findProperty(name, true, context);
			if(property != null)
			{
				if(classType.isDefinition())
				{
					return Ref.<PyType>create(PyBuiltinCache.getInstance(pyClass).getObjectType(PyNames.PROPERTY));
				}
				if(AccessDirection.of(this) == AccessDirection.READ)
				{
					final PyType type = property.getType(context);
					if(type != null)
					{
						return Ref.create(type);
					}
				}
				return Ref.create();
			}
		}
		else if(qualifierType instanceof PyUnionType)
		{
			final PyUnionType unionType = (PyUnionType) qualifierType;
			for(PyType type : unionType.getMembers())
			{
				final Ref<PyType> result = getTypeOfProperty(type, name, context);
				if(result != null)
				{
					return result;
				}
			}
		}
		return null;
	}

	@Nullable
	private PyType getTypeFromProviders(@Nonnull TypeEvalContext context)
	{
		for(PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME))
		{
			try
			{
				final PyType type = provider.getReferenceExpressionType(this, context);
				if(type != null)
				{
					return type;
				}
			}
			catch(AbstractMethodError e)
			{
				LOG.info(new ExtensionException(provider.getClass()));
			}
		}
		return null;
	}

	@Nullable
	private static PyType getTypeFromTarget(@Nonnull final PsiElement target, final TypeEvalContext context, PyReferenceExpression anchor)
	{
		final PyType type = getGenericTypeFromTarget(target, context, anchor);
		if(context.maySwitchToAST(anchor))
		{
			final PyExpression qualifier = anchor.getQualifier();
			if(qualifier != null)
			{
				if(!(type instanceof PyFunctionType) && PyTypeChecker.hasGenerics(type, context))
				{
					final Map<PyExpression, PyNamedParameter> parameters = Collections.emptyMap();
					final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(qualifier, parameters, context);
					if(substitutions != null && !substitutions.isEmpty())
					{
						final PyType substituted = PyTypeChecker.substitute(type, substitutions, context);
						if(substituted != null)
						{
							return substituted;
						}
					}
				}
			}
		}
		return type;
	}

	@Nullable
	private static PyType getGenericTypeFromTarget(@Nonnull final PsiElement target, final TypeEvalContext context, PyReferenceExpression anchor)
	{
		if(!(target instanceof PyTargetExpression))
		{  // PyTargetExpression will ask about its type itself
			final PyType pyType = getReferenceTypeFromProviders(target, context, anchor);
			if(pyType != null)
			{
				return pyType;
			}
		}
		if(target instanceof PyTargetExpression)
		{
			final String name = ((PyTargetExpression) target).getName();
			if(PyNames.NONE.equals(name))
			{
				return PyNoneType.INSTANCE;
			}
			if(PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name))
			{
				return PyBuiltinCache.getInstance(target).getBoolType();
			}
		}
		if(target instanceof PyFile)
		{
			return new PyModuleType((PyFile) target);
		}
		if(target instanceof PyImportedModule)
		{
			return new PyImportedModuleType((PyImportedModule) target);
		}
		if((target instanceof PyTargetExpression || target instanceof PyNamedParameter) && anchor != null && context.allowDataFlow(anchor))
		{
			final ScopeOwner scopeOwner = PsiTreeUtil.getStubOrPsiParentOfType(anchor, ScopeOwner.class);
			if(scopeOwner != null && scopeOwner == PsiTreeUtil.getStubOrPsiParentOfType(target, ScopeOwner.class))
			{
				final String name = ((PyElement) target).getName();
				if(name != null)
				{
					final PyType type = getTypeByControlFlow(name, context, anchor, scopeOwner);
					if(type != null)
					{
						return type;
					}
				}
			}
		}
		if(target instanceof PyFunction)
		{
			final PyDecoratorList decoratorList = ((PyFunction) target).getDecoratorList();
			if(decoratorList != null)
			{
				final PyDecorator propertyDecorator = decoratorList.findDecorator(PyNames.PROPERTY);
				if(propertyDecorator != null)
				{
					return PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY);
				}
				for(PyDecorator decorator : decoratorList.getDecorators())
				{
					final QualifiedName qName = decorator.getQualifiedName();
					if(qName != null && (qName.endsWith(PyNames.SETTER) || qName.endsWith(PyNames.DELETER) ||
							qName.endsWith(PyNames.GETTER)))
					{
						return PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY);
					}
				}
			}
		}
		if(target instanceof PyTypedElement)
		{
			return context.getType((PyTypedElement) target);
		}
		if(target instanceof PsiDirectory)
		{
			final PsiDirectory dir = (PsiDirectory) target;
			PsiFile file = dir.findFile(PyNames.INIT_DOT_PY);
			if(file != null)
			{
				return getTypeFromTarget(file, context, anchor);
			}
			if(PyUtil.isPackage(dir, anchor))
			{
				final PsiFile containingFile = anchor.getContainingFile();
				if(containingFile instanceof PyFile)
				{
					final QualifiedName qualifiedName = QualifiedNameFinder.findShortestImportableQName(dir);
					if(qualifiedName != null)
					{
						final PyImportedModule module = new PyImportedModule(null, (PyFile) containingFile, qualifiedName);
						return new PyImportedModuleType(module);
					}
				}
			}
		}
		return null;
	}

	private static PyType getTypeByControlFlow(@Nonnull String name, @Nonnull TypeEvalContext context, @Nonnull PyExpression anchor, @Nonnull ScopeOwner scopeOwner)
	{
		final PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(anchor, PyAugAssignmentStatement.class);
		final PyElement element = augAssignment != null ? augAssignment : anchor;
		try
		{
			final List<Instruction> defs = PyDefUseUtil.getLatestDefs(scopeOwner, name, element, true, false);
			if(!defs.isEmpty())
			{
				final ReadWriteInstruction firstInstruction = PyUtil.as(defs.get(0), ReadWriteInstruction.class);
				PyType type = firstInstruction != null ? firstInstruction.getType(context, anchor) : null;
				for(int i = 1; i < defs.size(); i++)
				{
					final ReadWriteInstruction instruction = PyUtil.as(defs.get(i), ReadWriteInstruction.class);
					type = PyUnionType.union(type, instruction != null ? instruction.getType(context, anchor) : null);
				}
				return type;
			}
		}
		catch(PyDefUseUtil.InstructionNotFoundException ignored)
		{
		}
		return null;
	}

	@Nullable
	public static PyType getReferenceTypeFromProviders(@Nonnull final PsiElement target, TypeEvalContext context, @Nullable PsiElement anchor)
	{
		for(PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME))
		{
			final PyType result = provider.getReferenceType(target, context, anchor);
			if(result != null)
			{
				return result;
			}
		}

		return null;
	}

	@Override
	public void subtreeChanged()
	{
		super.subtreeChanged();
		myQualifiedName = null;
	}

	private static class QualifiedResolveResultImpl extends RatedResolveResult implements QualifiedResolveResult
	{
		// a trivial implementation
		private List<PyExpression> myQualifiers;
		private boolean myIsImplicit;

		public boolean isImplicit()
		{
			return myIsImplicit;
		}

		private QualifiedResolveResultImpl(@Nonnull PsiElement element, List<PyExpression> qualifiers, boolean isImplicit)
		{
			super(isImplicit ? RATE_LOW : RATE_NORMAL, element);
			myQualifiers = qualifiers;
			myIsImplicit = isImplicit;
		}

		@Override
		public List<PyExpression> getQualifiers()
		{
			return myQualifiers;
		}
	}

	private static class QualifiedResolveResultEmpty implements QualifiedResolveResult
	{
		// a trivial implementation

		private QualifiedResolveResultEmpty()
		{
		}

		@Override
		public List<PyExpression> getQualifiers()
		{
			return Collections.emptyList();
		}

		public PsiElement getElement()
		{
			return null;
		}

		public boolean isValidResult()
		{
			return false;
		}

		public boolean isImplicit()
		{
			return false;
		}
	}

}

