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

import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.nameResolver.NameResolverTools;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.Maybe;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 * User: dcheryasov
 * Date: Dec 23, 2008 10:31:38 AM
 */
public class PyCallExpressionHelper
{
	private PyCallExpressionHelper()
	{
	}

	/**
	 * TODO: Copy/Paste with {@link PyArgumentList#addArgument(PyExpression)}
	 * Adds an argument to the end of argument list.
	 *
	 * @param us         the arg list
	 * @param expression what to add
	 */
	public static void addArgument(PyCallExpression us, PyExpression expression)
	{
		PyArgumentList argumentList = us.getArgumentList();
		if(argumentList != null)
		{
			PyExpression[] arguments = argumentList.getArguments();
			PyExpression last_arg = arguments.length == 0 ? null : arguments[arguments.length - 1];
			PyElementGenerator.getInstance(us.getProject()).insertItemIntoList(us, last_arg, expression);
		}
	}

	/**
	 * Tries to interpret a call as a call to built-in {@code classmethod} or {@code staticmethod}.
	 *
	 * @param redefiningCall the possible call, generally a result of chasing a chain of assignments
	 * @param us             any in-project PSI element, used to determine SDK and ultimately builtins module used to check the wrapping functions
	 * @return a pair of wrapper name and wrapped function; for {@code staticmethod(foo)} it would be ("staticmethod", foo).
	 */
	@Nullable
	public static Pair<String, PyFunction> interpretAsModifierWrappingCall(PyCallExpression redefiningCall, PsiElement us)
	{
		PyExpression redefining_callee = redefiningCall.getCallee();
		if(redefiningCall.isCalleeText(PyNames.CLASSMETHOD, PyNames.STATICMETHOD))
		{
			PyReferenceExpression referenceExpr = (PyReferenceExpression) redefining_callee;
			if(referenceExpr != null)
			{
				String refName = referenceExpr.getReferencedName();
				if((PyNames.CLASSMETHOD.equals(refName) || PyNames.STATICMETHOD.equals(refName)))
				{
					PsiElement redefining_func = referenceExpr.getReference().resolve();
					if(redefining_func != null)
					{
						PsiElement true_func = PyBuiltinCache.getInstance(us).getByName(refName);
						if(true_func instanceof PyClass)
						{
							true_func = ((PyClass) true_func).findInitOrNew(true, null);
						}
						if(true_func == redefining_func)
						{
							// yes, really a case of "foo = classmethod(foo)"
							PyArgumentList argumentList = redefiningCall.getArgumentList();
							if(argumentList != null)
							{ // really can't be any other way
								PyExpression[] args = argumentList.getArguments();
								if(args.length == 1)
								{
									PyExpression possible_original_ref = args[0];
									if(possible_original_ref instanceof PyReferenceExpression)
									{
										PsiElement original = ((PyReferenceExpression) possible_original_ref).getReference().resolve();
										if(original instanceof PyFunction)
										{
											// pinned down the original; replace our resolved callee with it and add flags.
											return Pair.create(refName, (PyFunction) original);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return null;
	}

	@Nullable
	public static PyClass resolveCalleeClass(PyCallExpression us)
	{
		PyExpression callee = us.getCallee();

		PsiElement resolved;
		QualifiedResolveResult resolveResult;
		if(callee instanceof PyReferenceExpression)
		{
			// dereference
			PyReferenceExpression ref = (PyReferenceExpression) callee;
			resolveResult = ref.followAssignmentsChain(PyResolveContext.noImplicits());
			resolved = resolveResult.getElement();
		}
		else
		{
			resolved = callee;
		}
		// analyze
		if(resolved instanceof PyClass)
		{
			return (PyClass) resolved;
		}
		else if(resolved instanceof PyFunction)
		{
			PyFunction pyFunction = (PyFunction) resolved;
			return pyFunction.getContainingClass();
		}

		return null;
	}

	@Nullable
	public static PyCallable resolveCalleeFunction(PyCallExpression call, PyResolveContext resolveContext)
	{
		PsiElement resolved;
		PyExpression callee = call.getCallee();
		if(callee instanceof PyReferenceExpression)
		{
			// dereference
			PyReferenceExpression ref = (PyReferenceExpression) callee;
			QualifiedResolveResult resolveResult = ref.followAssignmentsChain(resolveContext);
			resolved = resolveResult.getElement();
		}
		else
		{
			resolved = callee;
		}
		if(resolved instanceof PyClass)
		{
			resolved = ((PyClass) resolved).findInitOrNew(true, null); // class to constructor call
		}
		else if(resolved instanceof PyCallExpression)
		{
			PyCallExpression redefiningCall = (PyCallExpression) resolved;
			Pair<String, PyFunction> wrapperInfo = interpretAsModifierWrappingCall(redefiningCall, call);
			if(wrapperInfo != null)
			{
				resolved = wrapperInfo.getSecond();
			}
		}
		if(resolved instanceof PyCallable)
		{
			return (PyCallable) resolved;
		}
		return null;
	}


	@Nullable
	public static PyCallExpression.PyMarkedCallee resolveCallee(PyCallExpression us, PyResolveContext resolveContext)
	{
		return resolveCallee(us, resolveContext, 0);
	}

	@Nullable
	public static PyCallExpression.PyMarkedCallee resolveCallee(PyCallExpression us, PyResolveContext resolveContext, int implicitOffset)
	{
		PyFunction.Modifier wrappedModifier = null;
		boolean isConstructorCall = false;

		PyExpression callee = us.getCallee();
		if(isResolvedToMultipleTargets(callee, resolveContext))
		{
			return null;
		}
		PsiElement resolved;
		QualifiedResolveResult resolveResult = null;
		if(callee instanceof PyReferenceExpression)
		{
			// dereference
			PyReferenceExpression ref = (PyReferenceExpression) callee;
			resolveResult = ref.followAssignmentsChain(resolveContext);
			resolved = resolveResult.getElement();
		}
		else
		{
			resolved = callee;
		}
		// analyze
		if(resolved instanceof PyClass)
		{
			resolved = ((PyClass) resolved).findInitOrNew(true, resolveContext.getTypeEvalContext()); // class to constructor call
			isConstructorCall = true;
		}
		else if(resolved instanceof PyCallExpression)
		{
			// is it a case of "foo = classmethod(foo)"?
			PyCallExpression redefiningCall = (PyCallExpression) resolved;
			Pair<String, PyFunction> wrapperInfo = interpretAsModifierWrappingCall(redefiningCall, us);
			if(wrapperInfo != null)
			{
				resolved = wrapperInfo.getSecond();
				String wrapper_name = wrapperInfo.getFirst();
				if(PyNames.CLASSMETHOD.equals(wrapper_name))
				{
					wrappedModifier = PyFunction.Modifier.CLASSMETHOD;
				}
				else if(PyNames.STATICMETHOD.equals(wrapper_name))
				{
					wrappedModifier = PyFunction.Modifier.STATICMETHOD;
				}
			}
		}
		List<PyExpression> resolvedQualifiers = resolveResult != null ? resolveResult.getQualifiers() : null;
		List<PyExpression> qualifiers = resolvedQualifiers != null ? resolvedQualifiers : Collections.<PyExpression>emptyList();
		TypeEvalContext context = resolveContext.getTypeEvalContext();
		if(resolved instanceof PyFunction)
		{
			PyFunction function = (PyFunction) resolved;
			Property property = function.getProperty();
			if(property != null && isQualifiedByInstance(function, qualifiers, context))
			{
				PyType type = context.getReturnType(function);
				if(type instanceof PyFunctionTypeImpl)
				{
					resolved = ((PyFunctionTypeImpl) type).getCallable();
				}
				else
				{
					resolved = null;
				}
			}
		}
		if(resolved instanceof PyCallable)
		{
			PyFunction.Modifier modifier = resolved instanceof PyFunction ? ((PyFunction) resolved).getModifier() : null;
			if(modifier == null && wrappedModifier != null)
			{
				modifier = wrappedModifier;
			}
			boolean isByInstance = isConstructorCall || isQualifiedByInstance((PyCallable) resolved, qualifiers, context) || resolved instanceof PyBoundFunction;
			PyExpression lastQualifier = qualifiers.isEmpty() ? null : qualifiers.get(qualifiers.size() - 1);
			boolean isByClass = lastQualifier != null && isQualifiedByClass((PyCallable) resolved, lastQualifier, context);
			PyCallable callable = (PyCallable) resolved;

			implicitOffset += getImplicitArgumentCount(callable, modifier, isConstructorCall, isByInstance, isByClass);
			implicitOffset = implicitOffset < 0 ? 0 : implicitOffset; // wrong source can trigger strange behaviour
			return new PyCallExpression.PyMarkedCallee(callable, modifier, implicitOffset, resolveResult != null && resolveResult.isImplicit());
		}
		return null;
	}

	private static boolean isResolvedToMultipleTargets(@Nullable PyExpression callee, @Nonnull PyResolveContext resolveContext)
	{
		if(callee != null)
		{
			List<PsiElement> resolved = PyUtil.multiResolveTopPriority(callee, resolveContext);
			if(resolved.size() > 1)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Calls the {@link #getImplicitArgumentCount(PyCallable, PyFunction.Modifier, boolean, boolean, boolean)} full version}
	 * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
	 *
	 * @param callReference the call site, where arguments are given.
	 * @param function      resolved method which is being called; plain functions are OK but make little sense.
	 * @return a non-negative number of parameters that are implicit to this call.
	 */
	public static int getImplicitArgumentCount(@Nonnull PyReferenceExpression callReference, @Nonnull PyFunction function, @Nonnull PyResolveContext resolveContext)
	{
		PyDecorator decorator = PsiTreeUtil.getParentOfType(callReference, PyDecorator.class);
		if(decorator != null && PsiTreeUtil.isAncestor(decorator.getCallee(), callReference, false))
		{
			return 1;
		}
		QualifiedResolveResult followed = callReference.followAssignmentsChain(resolveContext);
		List<PyExpression> qualifiers = followed.getQualifiers();
		PyExpression firstQualifier = qualifiers != null && !qualifiers.isEmpty() ? qualifiers.get(0) : null;
		boolean isByInstance = isQualifiedByInstance(function, qualifiers != null ? qualifiers : Collections.<PyExpression>emptyList(), resolveContext.getTypeEvalContext());
		boolean isConstructorCall = isConstructorName(function.getName()) && (!callReference.isQualified() || !isConstructorName(callReference.getName()));
		boolean isByClass = firstQualifier != null && isQualifiedByClass(function, firstQualifier, resolveContext.getTypeEvalContext());
		return getImplicitArgumentCount(function, function.getModifier(), isConstructorCall, isByInstance, isByClass);
	}

	private static boolean isConstructorName(@Nullable String name)
	{
		return PyNames.NEW.equals(name) || PyNames.INIT.equals(name);
	}

	/**
	 * Finds how many arguments are implicit in a given call.
	 *
	 * @param callable     resolved method which is being called; non-methods immediately return 0.
	 * @param isByInstance true if the call is known to be by instance (not by class).
	 * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
	 * because one parameter ('self') is implicit.
	 */
	private static int getImplicitArgumentCount(PyCallable callable, PyFunction.Modifier modifier, boolean isConstructorCall, boolean isByInstance, boolean isByClass)
	{
		int implicit_offset = 0;
		boolean firstIsArgsOrKwargs = false;
		PyParameter[] parameters = callable.getParameterList().getParameters();
		if(parameters.length > 0)
		{
			PyParameter first = parameters[0];
			PyNamedParameter named = first.getAsNamed();
			if(named != null && (named.isPositionalContainer() || named.isKeywordContainer()))
			{
				firstIsArgsOrKwargs = true;
			}
		}
		if(!firstIsArgsOrKwargs && (isByInstance || isConstructorCall))
		{
			implicit_offset += 1;
		}
		PyFunction method = callable.asMethod();
		if(method == null)
		{
			return implicit_offset;
		}

		if(PyNames.NEW.equals(method.getName()))
		{
			return isConstructorCall ? 1 : 0;
		}
		if(!isByInstance && !isByClass && PyNames.INIT.equals(method.getName()))
		{
			return 1;
		}

		// decorators?
		if(modifier == PyFunction.Modifier.STATICMETHOD)
		{
			if(isByInstance && implicit_offset > 0)
			{
				implicit_offset -= 1; // might have marked it as implicit 'self'
			}
		}
		else if(modifier == PyFunction.Modifier.CLASSMETHOD)
		{
			if(!isByInstance)
			{
				implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
			}
		}
		return implicit_offset;
	}

	private static boolean isQualifiedByInstance(@Nullable PyCallable resolved, @Nonnull List<PyExpression> qualifiers, @Nonnull TypeEvalContext context)
	{
		PyDocStringOwner owner = PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyDocStringOwner.class);
		if(!(owner instanceof PyClass))
		{
			return false;
		}
		// true = call by instance
		if(qualifiers.isEmpty())
		{
			return true; // unqualified + method = implicit constructor call
		}
		for(PyExpression qualifier : qualifiers)
		{
			if(qualifier != null && isQualifiedByInstance(resolved, qualifier, context))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isQualifiedByInstance(@Nullable PyCallable resolved, @Nonnull PyExpression qualifier, @Nonnull TypeEvalContext context)
	{
		if(isQualifiedByClass(resolved, qualifier, context))
		{
			return false;
		}
		PyType qualifierType = context.getType(qualifier);
		if(qualifierType != null)
		{
			// TODO: handle UnionType
			if(qualifierType instanceof PyModuleType)
			{
				return false; // qualified by module, not instance.
			}
		}
		return true; // NOTE. best guess: unknown qualifier is more probably an instance.
	}

	private static boolean isQualifiedByClass(@Nullable PyCallable resolved, @Nonnull PyExpression qualifier, @Nonnull TypeEvalContext context)
	{
		PyType qualifierType = context.getType(qualifier);
		if(qualifierType instanceof PyClassType)
		{
			if(((PyClassType) qualifierType).isDefinition())
			{
				PyClass resolvedParent = PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyClass.class);
				if(resolvedParent != null)
				{
					PyClass qualifierClass = ((PyClassType) qualifierType).getPyClass();
					if((qualifierClass.isSubclass(resolvedParent, context) || resolvedParent.isSubclass(qualifierClass, context)))
					{
						return true;
					}
				}
			}
		}
		else if(qualifierType instanceof PyClassLikeType)
		{
			return ((PyClassLikeType) qualifierType).isDefinition(); //Any definition means callable is classmethod
		}
		return false;
	}

	static boolean isCalleeText(PyCallExpression pyCallExpression, String[] nameCandidates)
	{
		PyExpression callee = pyCallExpression.getCallee();
		if(!(callee instanceof PyReferenceExpression))
		{
			return false;
		}
		for(String name : nameCandidates)
		{
			if(name.equals(((PyReferenceExpression) callee).getReferencedName()))
			{
				return true;
			}
		}
		return false;
	}


	/**
	 * Returns argument if it exists and has appropriate type
	 *
	 * @param parameter  argument
	 * @param argClass   expected class
	 * @param expression call expression
	 * @param <T>        expected class
	 * @return argument expression or null if has wrong type of does not exist
	 */
	@Nullable
	public static <T extends PsiElement> T getArgument(@Nonnull FunctionParameter parameter, @Nonnull Class<T> argClass, @Nonnull PyCallExpression expression)
	{
		PyArgumentList list = expression.getArgumentList();
		if(list == null)
		{
			return null;
		}
		return PyUtil.as(list.getValueExpressionForParam(parameter), argClass);
	}

	@Nullable
	public static PyExpression getKeywordArgument(PyCallExpression expr, String keyword)
	{
		for(PyExpression arg : expr.getArguments())
		{
			if(arg instanceof PyKeywordArgument)
			{
				PyKeywordArgument kwarg = (PyKeywordArgument) arg;
				if(keyword.equals(kwarg.getKeyword()))
				{
					return kwarg.getValueExpression();
				}
			}
		}
		return null;
	}

	public static PyType getCallType(@Nonnull PyCallExpression call, @Nonnull TypeEvalContext context)
	{
		if(!TypeEvalStack.mayEvaluate(call))
		{
			return null;
		}
		try
		{
			PyExpression callee = call.getCallee();
			if(callee instanceof PyReferenceExpression)
			{
				// hardwired special cases
				if(PyNames.SUPER.equals(callee.getText()))
				{
					Maybe<PyType> superCallType = getSuperCallType(call, context);
					if(superCallType.isDefined())
					{
						return superCallType.value();
					}
				}
				if("type".equals(callee.getText()))
				{
					PyExpression[] args = call.getArguments();
					if(args.length == 1)
					{
						PyExpression arg = args[0];
						PyType argType = context.getType(arg);
						if(argType instanceof PyClassType)
						{
							PyClassType classType = (PyClassType) argType;
							if(!classType.isDefinition())
							{
								PyClass cls = classType.getPyClass();
								return context.getType(cls);
							}
						}
						else
						{
							return null;
						}
					}
				}
				// normal cases
				PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
				PsiPolyVariantReference reference = ((PyReferenceExpression) callee).getReference(resolveContext);
				List<PyType> members = new ArrayList<>();
				for(PsiElement target : PyUtil.multiResolveTopPriority(reference))
				{
					PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(target));
					if(target != null)
					{
						Ref<? extends PyType> typeRef = getCallTargetReturnType(call, target, context);
						if(typeRef != null)
						{
							members.add(typeRef.get());
						}
					}
				}
				if(!members.isEmpty())
				{
					return PyUnionType.union(members);
				}
			}
			if(callee == null)
			{
				return null;
			}
			else
			{
				PyType type = context.getType(callee);
				if(type instanceof PyCallableType)
				{
					PyCallableType callableType = (PyCallableType) type;
					return callableType.getCallType(context, call);
				}
				if(type instanceof PyUnionType)
				{
					return getCallResultTypeFromUnion(call, context, (PyUnionType) type);
				}
				return null;
			}
		}
		finally
		{
			TypeEvalStack.evaluated(call);
		}
	}

	/**
	 * @return type that union will return if you call it
	 */
	@Nullable
	private static PyType getCallResultTypeFromUnion(@Nonnull PyCallSiteExpression call, @Nonnull TypeEvalContext context, @Nonnull PyUnionType type)
	{
		Collection<PyType> callResultTypes = new HashSet<>();

		for(PyType memberType : type.getMembers())
		{
			Boolean callable = PyTypeChecker.isCallable(memberType);
			if(!((callable != null && callable && memberType instanceof PyCallableType)))
			{
				continue;
			}
			PyCallableType callableMemberType = (PyCallableType) memberType;

			if(!callableMemberType.isCallable())
			{
				continue;
			}
			PyType callResultType = callableMemberType.getCallType(context, call);
			if(callResultType != null)
			{
				callResultTypes.add(callResultType);
			}
		}

		return PyUnionType.union(callResultTypes);
	}

	@Nullable
	private static Ref<? extends PyType> getCallTargetReturnType(@Nonnull PyCallExpression call, @Nonnull PsiElement target, @Nonnull TypeEvalContext context)
	{
		PyClass cls = null;
		PyFunction init = null;
		if(target instanceof PyClass)
		{
			cls = (PyClass) target;
			init = cls.findInitOrNew(true, context);
		}
		else if(target instanceof PyFunction)
		{
			PyFunction f = (PyFunction) target;
			if(PyNames.INIT.equals(f.getName()))
			{
				init = f;
				cls = f.getContainingClass();
			}
		}
		if(init != null)
		{
			PyType t = init.getCallType(context, call);
			if(cls != null)
			{
				if(init.getContainingClass() != cls)
				{
					if(t instanceof PyCollectionType)
					{
						List<PyType> elementTypes = ((PyCollectionType) t).getElementTypes(context);
						return Ref.create(new PyCollectionTypeImpl(cls, false, elementTypes));
					}
					return Ref.create(new PyClassTypeImpl(cls, false));
				}
			}
			if(t != null && !(t instanceof PyNoneType))
			{
				return Ref.create(t);
			}
			if(cls != null && t == null)
			{
				PyFunction newMethod = cls.findMethodByName(PyNames.NEW, true, null);
				if(newMethod != null && !PyBuiltinCache.getInstance(call).isBuiltin(newMethod))
				{
					return Ref.create(PyUnionType.createWeakType(new PyClassTypeImpl(cls, false)));
				}
			}
		}
		if(cls != null)
		{
			return Ref.create(new PyClassTypeImpl(cls, false));
		}
		PyType providedType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(target, context, call);
		if(providedType instanceof PyCallableType)
		{
			return Ref.create(((PyCallableType) providedType).getCallType(context, call));
		}
		if(target instanceof PyCallable)
		{
			PyCallable callable = (PyCallable) target;
			return Ref.create(callable.getCallType(context, call));
		}
		return null;
	}

	@Nonnull
	private static Maybe<PyType> getSuperCallType(@Nonnull PyCallExpression call, TypeEvalContext context)
	{
		PyExpression callee = call.getCallee();
		if(callee instanceof PyReferenceExpression)
		{
			PsiElement must_be_super_init = ((PyReferenceExpression) callee).getReference().resolve();
			if(must_be_super_init instanceof PyFunction)
			{
				PyClass must_be_super = ((PyFunction) must_be_super_init).getContainingClass();
				if(must_be_super == PyBuiltinCache.getInstance(call).getClass(PyNames.SUPER))
				{
					PyArgumentList argumentList = call.getArgumentList();
					if(argumentList != null)
					{
						PyClass containingClass = PsiTreeUtil.getParentOfType(call, PyClass.class);
						PyExpression[] args = argumentList.getArguments();
						if(args.length > 1)
						{
							PyExpression first_arg = args[0];
							if(first_arg instanceof PyReferenceExpression)
							{
								PyReferenceExpression firstArgRef = (PyReferenceExpression) first_arg;
								PyExpression qualifier = firstArgRef.getQualifier();
								if(qualifier != null && PyNames.__CLASS__.equals(firstArgRef.getReferencedName()))
								{
									PsiReference qRef = qualifier.getReference();
									PsiElement element = qRef == null ? null : qRef.resolve();
									if(element instanceof PyParameter)
									{
										PyParameterList parameterList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
										if(parameterList != null && element == parameterList.getParameters()[0])
										{
											return new Maybe<>(getSuperCallTypeForArguments(context, containingClass, args[1]));
										}
									}
								}
								PsiElement possible_class = firstArgRef.getReference().resolve();
								if(possible_class instanceof PyClass && ((PyClass) possible_class).isNewStyleClass(context))
								{
									PyClass first_class = (PyClass) possible_class;
									return new Maybe<>(getSuperCallTypeForArguments(context, first_class, args[1]));
								}
							}
						}
						else if((call.getContainingFile() instanceof PyFile) &&
								((PyFile) call.getContainingFile()).getLanguageLevel().isPy3K() &&
								(containingClass != null))
						{
							return new Maybe<>(getSuperClassUnionType(containingClass, context));
						}
					}
				}
			}
		}
		return new Maybe<>();
	}

	@Nullable
	private static PyType getSuperCallTypeForArguments(TypeEvalContext context, PyClass firstClass, PyExpression second_arg)
	{
		// check 2nd argument, too; it should be an instance
		if(second_arg != null)
		{
			PyType second_type = context.getType(second_arg);
			if(second_type instanceof PyClassType)
			{
				// imitate isinstance(second_arg, possible_class)
				PyClass secondClass = ((PyClassType) second_type).getPyClass();
				if(CompletionUtilCore.getOriginalOrSelf(firstClass) == secondClass)
				{
					return getSuperClassUnionType(firstClass, context);
				}
				if(secondClass.isSubclass(firstClass, context))
				{
					Iterator<PyClass> iterator = firstClass.getAncestorClasses(context).iterator();
					if(iterator.hasNext())
					{
						return new PyClassTypeImpl(iterator.next(), false); // super(Foo, self) has type of Foo, modulo __get__()
					}
				}
			}
		}
		return null;
	}

	@Nullable
	private static PyType getSuperClassUnionType(@Nonnull PyClass pyClass, TypeEvalContext context)
	{
		// TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
		// super can also delegate to sibling types
		// TODO handle __mro__ here
		PyClass[] supers = pyClass.getSuperClasses(context);
		if(supers.length > 0)
		{
			if(supers.length == 1)
			{
				return new PyClassTypeImpl(supers[0], false);
			}
			List<PyType> superTypes = new ArrayList<>();
			for(PyClass aSuper : supers)
			{
				superTypes.add(new PyClassTypeImpl(aSuper, false));
			}
			return PyUnionType.union(superTypes);
		}
		return null;
	}

	/**
	 * Checks if expression callee's name matches one of names, provided by appropriate {@link FQNamesProvider}
	 *
	 * @param expression     call expression
	 * @param namesProviders name providers to check name against
	 * @return true if matches
	 * @see com.jetbrains.python.nameResolver
	 */
	public static boolean isCallee(@Nonnull PyCallExpression expression, @Nonnull FQNamesProvider... namesProviders)
	{
		PyExpression callee = expression.getCallee();
		return (callee != null) && NameResolverTools.isName(callee, namesProviders);
	}

	@Nonnull
	public static PyCallExpression.PyArgumentsMapping mapArguments(@Nonnull PyCallExpression callExpression, @Nonnull PyResolveContext resolveContext, int implicitOffset)
	{

		PyArgumentList argumentList = callExpression.getArgumentList();
		PyCallExpression.PyMarkedCallee markedCallee = callExpression.resolveCallee(resolveContext, implicitOffset);

		if(markedCallee == null || argumentList == null)
		{
			return new PyCallExpression.PyArgumentsMapping(callExpression, null, Collections.<PyExpression, PyNamedParameter>emptyMap(), Collections.<PyParameter>emptyList(),
					Collections.<PyExpression>emptyList(), Collections.<PyNamedParameter>emptyList(), Collections.<PyNamedParameter>emptyList(), Collections.<PyExpression,
					PyTupleParameter>emptyMap());
		}
		TypeEvalContext context = resolveContext.getTypeEvalContext();
		List<PyParameter> parameters = PyUtil.getParameters(markedCallee.getCallable(), context);
		List<PyParameter> explicitParameters = dropImplicitParameters(parameters, markedCallee.getImplicitOffset());
		List<PyExpression> arguments = new ArrayList<>(Arrays.asList(argumentList.getArguments()));
		ArgumentMappingResults mappingResults = analyzeArguments(arguments, explicitParameters);
		return new PyCallExpression.PyArgumentsMapping(callExpression, markedCallee, mappingResults.getMappedParameters(), mappingResults.getUnmappedParameters(),
				mappingResults.getUnmappedArguments(), mappingResults.getParametersMappedToVariadicPositionalArguments(), mappingResults.getParametersMappedToVariadicKeywordArguments(),
				mappingResults.getMappedTupleParameters());
	}

	@Nonnull
	public static Map<PyExpression, PyNamedParameter> mapArguments(@Nonnull PyCallSiteExpression callSite,
			@Nonnull PyCallable callable,
			@Nonnull List<PyParameter> parameters,
			@Nonnull TypeEvalContext context)
	{
		List<PyExpression> arguments = PyTypeChecker.getArguments(callSite, callable);
		PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
		List<PyParameter> explicitParameters = PyTypeChecker.filterExplicitParameters(parameters, callable, callSite, resolveContext);
		return analyzeArguments(arguments, explicitParameters).getMappedParameters();
	}

	@Nonnull
	public static Map<PyExpression, PyNamedParameter> mapArguments(@Nonnull PyCallSiteExpression callSite, @Nonnull PyCallable callable, @Nonnull TypeEvalContext context)
	{
		List<PyParameter> parameters = PyUtil.getParameters(callable, context);
		return mapArguments(callSite, callable, parameters, context);
	}

	@Nonnull
	private static ArgumentMappingResults analyzeArguments(@Nonnull List<PyExpression> arguments, @Nonnull List<PyParameter> parameters)
	{
		boolean seenSingleStar = false;
		boolean mappedVariadicArgumentsToParameters = false;
		Map<PyExpression, PyNamedParameter> mappedParameters = new LinkedHashMap<>();
		List<PyParameter> unmappedParameters = new ArrayList<>();
		List<PyExpression> unmappedArguments = new ArrayList<>();
		List<PyNamedParameter> parametersMappedToVariadicKeywordArguments = new ArrayList<>();
		List<PyNamedParameter> parametersMappedToVariadicPositionalArguments = new ArrayList<>();
		Map<PyExpression, PyTupleParameter> tupleMappedParameters = new LinkedHashMap<>();

		PositionalArgumentsAnalysisResults positionalResults = filterPositionalAndVariadicArguments(arguments);
		List<PyKeywordArgument> keywordArguments = filterKeywordArguments(arguments);
		List<PyExpression> variadicPositionalArguments = positionalResults.variadicPositionalArguments;
		Set<PyExpression> positionalComponentsOfVariadicArguments = new LinkedHashSet<>(positionalResults.componentsOfVariadicPositionalArguments);
		List<PyExpression> variadicKeywordArguments = filterVariadicKeywordArguments(arguments);

		List<PyExpression> allPositionalArguments = positionalResults.allPositionalArguments;

		for(PyParameter parameter : parameters)
		{
			if(parameter instanceof PyNamedParameter)
			{
				PyNamedParameter namedParameter = (PyNamedParameter) parameter;
				String parameterName = namedParameter.getName();
				if(namedParameter.isPositionalContainer())
				{
					for(PyExpression argument : allPositionalArguments)
					{
						mappedParameters.put(argument, namedParameter);
					}
					if(variadicPositionalArguments.size() == 1)
					{
						mappedParameters.put(variadicPositionalArguments.get(0), namedParameter);
					}
					allPositionalArguments.clear();
					variadicPositionalArguments.clear();
				}
				else if(namedParameter.isKeywordContainer())
				{
					for(PyKeywordArgument argument : keywordArguments)
					{
						mappedParameters.put(argument, namedParameter);
					}
					if(variadicKeywordArguments.size() == 1)
					{
						mappedParameters.put(variadicKeywordArguments.get(0), namedParameter);
					}
					keywordArguments.clear();
					variadicKeywordArguments.clear();
				}
				else if(seenSingleStar)
				{
					PyExpression keywordArgument = removeKeywordArgument(keywordArguments, parameterName);
					if(keywordArgument != null)
					{
						mappedParameters.put(keywordArgument, namedParameter);
					}
					else if(variadicKeywordArguments.isEmpty())
					{
						if(!namedParameter.hasDefaultValue())
						{
							unmappedParameters.add(namedParameter);
						}
					}
					else
					{
						parametersMappedToVariadicKeywordArguments.add(namedParameter);
					}
				}
				else
				{
					if(allPositionalArguments.isEmpty())
					{
						PyKeywordArgument keywordArgument = removeKeywordArgument(keywordArguments, parameterName);
						if(keywordArgument != null)
						{
							mappedParameters.put(keywordArgument, namedParameter);
						}
						else if(variadicPositionalArguments.isEmpty() && variadicKeywordArguments.isEmpty() && !namedParameter.hasDefaultValue())
						{
							unmappedParameters.add(namedParameter);
						}
						else
						{
							if(!variadicPositionalArguments.isEmpty())
							{
								parametersMappedToVariadicPositionalArguments.add(namedParameter);
							}
							if(!variadicKeywordArguments.isEmpty())
							{
								parametersMappedToVariadicKeywordArguments.add(namedParameter);
							}
							mappedVariadicArgumentsToParameters = true;
						}
					}
					else
					{
						PyExpression positionalArgument = next(allPositionalArguments);
						if(positionalArgument != null)
						{
							mappedParameters.put(positionalArgument, namedParameter);
							if(positionalComponentsOfVariadicArguments.contains(positionalArgument))
							{
								parametersMappedToVariadicPositionalArguments.add(namedParameter);
							}
						}
						else if(!namedParameter.hasDefaultValue())
						{
							unmappedParameters.add(namedParameter);
						}
					}
				}
			}
			else if(parameter instanceof PyTupleParameter)
			{
				PyExpression positionalArgument = next(allPositionalArguments);
				if(positionalArgument != null)
				{
					PyTupleParameter tupleParameter = (PyTupleParameter) parameter;
					tupleMappedParameters.put(positionalArgument, tupleParameter);
					TupleMappingResults tupleMappingResults = mapComponentsOfTupleParameter(positionalArgument, tupleParameter);
					mappedParameters.putAll(tupleMappingResults.getParameters());
					unmappedParameters.addAll(tupleMappingResults.getUnmappedParameters());
					unmappedArguments.addAll(tupleMappingResults.getUnmappedArguments());
				}
				else if(variadicPositionalArguments.isEmpty())
				{
					if(!parameter.hasDefaultValue())
					{
						unmappedParameters.add(parameter);
					}
				}
				else
				{
					mappedVariadicArgumentsToParameters = true;
				}
			}
			else if(parameter instanceof PySingleStarParameter)
			{
				seenSingleStar = true;
			}
			else if(!parameter.hasDefaultValue())
			{
				unmappedParameters.add(parameter);
			}
		}

		if(mappedVariadicArgumentsToParameters)
		{
			variadicPositionalArguments.clear();
			variadicKeywordArguments.clear();
		}

		unmappedArguments.addAll(allPositionalArguments);
		unmappedArguments.addAll(keywordArguments);
		unmappedArguments.addAll(variadicPositionalArguments);
		unmappedArguments.addAll(variadicKeywordArguments);

		return new ArgumentMappingResults(mappedParameters, unmappedParameters, unmappedArguments, parametersMappedToVariadicPositionalArguments, parametersMappedToVariadicKeywordArguments,
				tupleMappedParameters);
	}

	public static class ArgumentMappingResults
	{
		@Nonnull
		private final Map<PyExpression, PyNamedParameter> myMappedParameters;
		@Nonnull
		private final List<PyParameter> myUnmappedParameters;
		@Nonnull
		private final List<PyExpression> myUnmappedArguments;
		@Nonnull
		private final List<PyNamedParameter> myParametersMappedToVariadicPositionalArguments;
		@Nonnull
		private final List<PyNamedParameter> myParametersMappedToVariadicKeywordArguments;
		@Nonnull
		private final Map<PyExpression, PyTupleParameter> myMappedTupleParameters;

		public ArgumentMappingResults(@Nonnull Map<PyExpression, PyNamedParameter> mappedParameters,
				@Nonnull List<PyParameter> unmappedParameters,
				@Nonnull List<PyExpression> unmappedArguments,
				@Nonnull List<PyNamedParameter> parametersMappedToVariadicPositionalArguments,
				@Nonnull List<PyNamedParameter> parametersMappedToVariadicKeywordArguments,
				@Nonnull Map<PyExpression, PyTupleParameter> mappedTupleParameters)
		{
			myMappedParameters = mappedParameters;
			myUnmappedParameters = unmappedParameters;
			myUnmappedArguments = unmappedArguments;
			myParametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
			myParametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
			myMappedTupleParameters = mappedTupleParameters;
		}

		@Nonnull
		public Map<PyExpression, PyNamedParameter> getMappedParameters()
		{
			return myMappedParameters;
		}

		@Nonnull
		public List<PyParameter> getUnmappedParameters()
		{
			return myUnmappedParameters;
		}

		@Nonnull
		public List<PyExpression> getUnmappedArguments()
		{
			return myUnmappedArguments;
		}

		@Nonnull
		public List<PyNamedParameter> getParametersMappedToVariadicPositionalArguments()
		{
			return myParametersMappedToVariadicPositionalArguments;
		}

		@Nonnull
		public List<PyNamedParameter> getParametersMappedToVariadicKeywordArguments()
		{
			return myParametersMappedToVariadicKeywordArguments;
		}

		@Nonnull
		public Map<PyExpression, PyTupleParameter> getMappedTupleParameters()
		{
			return myMappedTupleParameters;
		}
	}

	private static class TupleMappingResults
	{
		@Nonnull
		private final Map<PyExpression, PyNamedParameter> myParameters;
		@Nonnull
		private final List<PyParameter> myUnmappedParameters;
		@Nonnull
		private final List<PyExpression> myUnmappedArguments;

		TupleMappingResults(@Nonnull Map<PyExpression, PyNamedParameter> mappedParameters, @Nonnull List<PyParameter> unmappedParameters, @Nonnull List<PyExpression> unmappedArguments)
		{

			myParameters = mappedParameters;
			myUnmappedParameters = unmappedParameters;
			myUnmappedArguments = unmappedArguments;
		}

		@Nonnull
		public Map<PyExpression, PyNamedParameter> getParameters()
		{
			return myParameters;
		}

		@Nonnull
		public List<PyParameter> getUnmappedParameters()
		{
			return myUnmappedParameters;
		}

		@Nonnull
		public List<PyExpression> getUnmappedArguments()
		{
			return myUnmappedArguments;
		}
	}

	@Nonnull
	private static TupleMappingResults mapComponentsOfTupleParameter(@Nullable PyExpression argument, @Nonnull PyTupleParameter parameter)
	{
		List<PyParameter> unmappedParameters = new ArrayList<>();
		List<PyExpression> unmappedArguments = new ArrayList<>();
		Map<PyExpression, PyNamedParameter> mappedParameters = new LinkedHashMap<>();
		argument = PyPsiUtils.flattenParens(argument);
		if(argument instanceof PySequenceExpression)
		{
			PySequenceExpression sequenceExpr = (PySequenceExpression) argument;
			PyExpression[] argumentComponents = sequenceExpr.getElements();
			PyParameter[] parameterComponents = parameter.getContents();
			for(int i = 0; i < parameterComponents.length; i++)
			{
				PyParameter param = parameterComponents[i];
				if(i < argumentComponents.length)
				{
					PyExpression arg = argumentComponents[i];
					if(arg != null)
					{
						if(param instanceof PyNamedParameter)
						{
							mappedParameters.put(arg, (PyNamedParameter) param);
						}
						else if(param instanceof PyTupleParameter)
						{
							TupleMappingResults nestedResults = mapComponentsOfTupleParameter(arg, (PyTupleParameter) param);
							mappedParameters.putAll(nestedResults.getParameters());
							unmappedParameters.addAll(nestedResults.getUnmappedParameters());
							unmappedArguments.addAll(nestedResults.getUnmappedArguments());
						}
						else
						{
							unmappedArguments.add(arg);
						}
					}
					else
					{
						unmappedParameters.add(param);
					}
				}
				else
				{
					unmappedParameters.add(param);
				}
			}
			if(argumentComponents.length > parameterComponents.length)
			{
				for(int i = parameterComponents.length; i < argumentComponents.length; i++)
				{
					PyExpression arg = argumentComponents[i];
					if(arg != null)
					{
						unmappedArguments.add(arg);
					}
				}
			}
		}
		return new TupleMappingResults(mappedParameters, unmappedParameters, unmappedArguments);
	}

	@Nullable
	private static PyKeywordArgument removeKeywordArgument(@Nonnull List<PyKeywordArgument> arguments, @Nullable String name)
	{
		PyKeywordArgument result = null;
		for(PyKeywordArgument argument : arguments)
		{
			String keyword = argument.getKeyword();
			if(keyword != null && keyword.equals(name))
			{
				result = argument;
				break;
			}
		}
		if(result != null)
		{
			arguments.remove(result);
		}
		return result;
	}

	@Nonnull
	private static List<PyKeywordArgument> filterKeywordArguments(@Nonnull List<PyExpression> arguments)
	{
		List<PyKeywordArgument> results = new ArrayList<>();
		for(PyExpression argument : arguments)
		{
			if(argument instanceof PyKeywordArgument)
			{
				results.add((PyKeywordArgument) argument);
			}
		}
		return results;
	}

	private static class PositionalArgumentsAnalysisResults
	{
		@Nonnull
		private final List<PyExpression> allPositionalArguments;
		@Nonnull
		private final List<PyExpression> componentsOfVariadicPositionalArguments;
		@Nonnull
		private final List<PyExpression> variadicPositionalArguments;

		public PositionalArgumentsAnalysisResults(@Nonnull List<PyExpression> allPositionalArguments,
				@Nonnull List<PyExpression> componentsOfVariadicPositionalArguments,
				@Nonnull List<PyExpression> variadicPositionalArguments)
		{
			this.allPositionalArguments = allPositionalArguments;
			this.componentsOfVariadicPositionalArguments = componentsOfVariadicPositionalArguments;
			this.variadicPositionalArguments = variadicPositionalArguments;
		}
	}

	@Nonnull
	private static PositionalArgumentsAnalysisResults filterPositionalAndVariadicArguments(@Nonnull List<PyExpression> arguments)
	{
		List<PyExpression> variadicArguments = new ArrayList<>();
		List<PyExpression> allPositionalArguments = new ArrayList<>();
		List<PyExpression> componentsOfVariadicPositionalArguments = new ArrayList<>();
		boolean seenVariadicPositionalArgument = false;
		boolean seenVariadicKeywordArgument = false;
		boolean seenKeywordArgument = false;
		for(PyExpression argument : arguments)
		{
			if(argument instanceof PyStarArgument)
			{
				if(((PyStarArgument) argument).isKeyword())
				{
					seenVariadicKeywordArgument = true;
				}
				else
				{
					seenVariadicPositionalArgument = true;
					PsiElement expr = PyPsiUtils.flattenParens(PsiTreeUtil.getChildOfType(argument, PyExpression.class));
					if(expr instanceof PySequenceExpression)
					{
						PySequenceExpression sequenceExpr = (PySequenceExpression) expr;
						List<PyExpression> elements = Arrays.asList(sequenceExpr.getElements());
						allPositionalArguments.addAll(elements);
						componentsOfVariadicPositionalArguments.addAll(elements);
					}
					else
					{
						variadicArguments.add(argument);
					}
				}
			}
			else if(argument instanceof PyKeywordArgument)
			{
				seenKeywordArgument = true;
			}
			else
			{
				if(seenKeywordArgument ||
						seenVariadicKeywordArgument ||
						seenVariadicPositionalArgument && LanguageLevel.forElement(argument).isOlderThan(LanguageLevel.PYTHON35))
				{
					continue;
				}
				allPositionalArguments.add(argument);
			}
		}
		return new PositionalArgumentsAnalysisResults(allPositionalArguments, componentsOfVariadicPositionalArguments, variadicArguments);
	}

	@Nonnull
	private static List<PyExpression> filterVariadicKeywordArguments(@Nonnull List<PyExpression> arguments)
	{
		List<PyExpression> results = new ArrayList<>();
		for(PyExpression argument : arguments)
		{
			if(argument != null && isVariadicKeywordArgument(argument))
			{
				results.add(argument);
			}
		}
		return results;
	}

	public static boolean isVariadicKeywordArgument(@Nonnull PyExpression argument)
	{
		return argument instanceof PyStarArgument && ((PyStarArgument) argument).isKeyword();
	}

	public static boolean isVariadicPositionalArgument(@Nonnull PyExpression argument)
	{
		return argument instanceof PyStarArgument && !((PyStarArgument) argument).isKeyword();
	}

	@Nullable
	private static <T> T next(@Nonnull List<T> list)
	{
		return list.isEmpty() ? null : list.remove(0);
	}

	@Nonnull
	private static List<PyParameter> dropImplicitParameters(@Nonnull List<PyParameter> parameters, int offset)
	{
		ArrayList<PyParameter> results = new ArrayList<>(parameters);
		for(int i = 0; i < offset && !results.isEmpty(); i++)
		{
			results.remove(0);
		}
		return results;
	}
}
