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
package com.jetbrains.python.impl.codeInsight;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static consulo.util.collection.ContainerUtil.list;

/**
 * @author vlan
 */
public class PyTypingTypeProvider extends PyTypeProviderBase
{
	public static final Pattern TYPE_COMMENT_PATTERN = Pattern.compile("# *type: *(.*)");

	private static ImmutableMap<String, String> COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
        .put("typing.List", "list")
        .put("typing.Dict", "dict")
        .put("typing.Set", PyNames.SET)
        .put("typing.FrozenSet", "frozenset")
        .put("typing.Tuple", PyNames.TUPLE)
        .put("typing.Iterable", PyNames.COLLECTIONS + "." + PyNames.ITERABLE)
        .put("typing.Iterator", PyNames.COLLECTIONS + "." + PyNames.ITERATOR)
        .put("typing.Container", PyNames.COLLECTIONS + "." + PyNames.CONTAINER)
        .put("typing.Sequence", PyNames.COLLECTIONS + "." + PyNames.SEQUENCE)
        .put("typing.MutableSequence", PyNames.COLLECTIONS + "." + "MutableSequence")
        .put("typing.Mapping", PyNames.COLLECTIONS + "." + PyNames.MAPPING)
        .put("typing.MutableMapping", PyNames.COLLECTIONS + "." + "MutableMapping")
        .put("typing.AbstractSet", PyNames.COLLECTIONS + "." + "Set")
        .put("typing.MutableSet", PyNames.COLLECTIONS + "." + "MutableSet")
        .build();

	public static ImmutableMap<String, String> TYPING_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
        .put("list", "List")
        .put("dict", "Dict")
        .put("set", "Set")
        .put("frozenset", "FrozenSet")
        .build();

	private static ImmutableSet<String> GENERIC_CLASSES = ImmutableSet.<String>builder()
        .add("typing.Generic")
        .add("typing.AbstractGeneric")
        .add("typing.Protocol")
        .build();

	@Nullable
    @Override
    @RequiredReadAction
	public SimpleReference<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context)
	{
		PyAnnotation annotation = param.getAnnotation();
		if(annotation != null)
		{
			// XXX: Requires switching from stub to AST
			PyExpression value = annotation.getValue();
			if(value != null)
			{
				PyType type = getType(value, new Context(context));
				if(type != null)
				{
					PyType optionalType = getOptionalTypeFromDefaultNone(param, type, context);
					return SimpleReference.create(optionalType != null ? optionalType : type);
				}
			}
		}

		String paramComment = param.getTypeCommentAnnotation();
		if(paramComment != null)
		{
			return SimpleReference.create(getStringBasedType(paramComment, param, new Context(context)));
		}

		String comment = func.getTypeCommentAnnotation();
		if(comment != null)
		{
			PyTypeParser.ParseResult result = PyTypeParser.parsePep484FunctionTypeComment(param, comment);
			PyCallableType functionType = as(result.getType(), PyCallableType.class);
			if(functionType != null)
			{
				List<PyCallableParameter> paramTypes = functionType.getParameters(context);
				// Function annotation of kind (...) -> Type
				if(paramTypes == null)
				{
					return SimpleReference.create();
				}
				PyParameter[] funcParams = func.getParameterList().getParameters();
				int startOffset = omitFirstParamInTypeComment(func) ? 1 : 0;
				for(int paramIndex = 0; paramIndex < funcParams.length; paramIndex++)
				{
					if(funcParams[paramIndex] == param)
					{
						int typeIndex = paramIndex - startOffset;
						if(typeIndex >= 0 && typeIndex < paramTypes.size())
						{
							return SimpleReference.create(paramTypes.get(typeIndex).getType(context));
						}
						break;
					}
				}
			}
		}
		return null;
	}

	private static boolean omitFirstParamInTypeComment(PyFunction func)
	{
		return func.getContainingClass() != null && func.getModifier() != PyFunction.Modifier.STATICMETHOD;
	}

	@Nullable
	@Override
    @RequiredReadAction
	public SimpleReference<PyType> getReturnType(PyCallable callable, TypeEvalContext context)
	{
		if(callable instanceof PyFunction function)
		{
			PyAnnotation annotation = function.getAnnotation();
			if(annotation != null)
			{
				// XXX: Requires switching from stub to AST
				PyExpression value = annotation.getValue();
				if(value != null)
				{
					PyType type = getType(value, new Context(context));
					return type != null ? SimpleReference.create(type) : null;
				}
			}
			PyType constructorType = getGenericConstructorType(function, new Context(context));
			if(constructorType != null)
			{
				return SimpleReference.create(constructorType);
			}
			String comment = function.getTypeCommentAnnotation();
			if(comment != null)
			{
				PyTypeParser.ParseResult result = PyTypeParser.parsePep484FunctionTypeComment(callable, comment);
				PyCallableType funcType = as(result.getType(), PyCallableType.class);
				if(funcType != null)
				{
					return SimpleReference.create(funcType.getReturnType(context));
				}
			}
		}
		return null;
	}

	@Nullable
	@Override
    @RequiredReadAction
	public SimpleReference<PyType> getCallType(PyFunction function, @Nullable PyCallSiteExpression callSite, TypeEvalContext context)
	{
		if("typing.cast".equals(function.getQualifiedName()))
		{
			return Optional.ofNullable(as(callSite, PyCallExpression.class))
                .map(PyCallExpression::getArguments)
                .filter(args -> args.length > 0)
                .map(args -> getType(args[0], new Context(context)))
                .map(SimpleReference::create)
                .orElse(null);
		}

		return null;
	}

	@Override
    @RequiredReadAction
	public PyType getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor)
	{
		if(referenceTarget instanceof PyTargetExpression)
		{
			PyTargetExpression target = (PyTargetExpression) referenceTarget;
			if(context.maySwitchToAST(target))
			{
				// XXX: Requires switching from stub to AST
				PyAnnotation annotation = target.getAnnotation();
				if(annotation != null)
				{
					PyExpression value = annotation.getValue();
					if(value != null)
					{
						return getType(value, new Context(context));
					}
					return null;
				}
			}
			String comment = target.getTypeCommentAnnotation();
			if(comment != null)
			{
				PyType type = getStringBasedType(comment, referenceTarget, new Context(context));
				if(type instanceof PyTupleType tupleType)
				{
					PyTupleExpression tupleExpr = PsiTreeUtil.getParentOfType(target, PyTupleExpression.class);
					if(tupleExpr != null)
					{
						return PyTypeChecker.getTargetTypeFromTupleAssignment(target, tupleExpr, tupleType);
					}
				}
				return type;
			}
		}
		return null;
	}

	/**
	 * Checks that text of a comment starts with the "type:" prefix and returns trimmed part afterwards. This trailing part is supposed to
	 * contain type annotation in PEP 484 compatible format, that can be parsed with either {@link PyTypeParser#parse(PsiElement, String)}
	 * or {@link PyTypeParser#parsePep484FunctionTypeComment(PsiElement, String)}.
	 */
	@Nullable
	public static String getTypeCommentValue(String text)
	{
		Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
		if(m.matches())
		{
			return m.group(1);
		}
		return null;
	}

	private static boolean isAny(PyType type)
	{
		return type instanceof PyClassType && "typing.Any".equals(((PyClassType) type).getPyClass().getQualifiedName());
	}

	@Nullable
	private static PyType getOptionalTypeFromDefaultNone(PyNamedParameter param, PyType type, TypeEvalContext context)
	{
		PyExpression defaultValue = param.getDefaultValue();
		if(defaultValue != null)
		{
			PyType defaultType = context.getType(defaultValue);
			if(defaultType instanceof PyNoneType)
			{
				return PyUnionType.union(type, defaultType);
			}
		}
		return null;
	}

	@Nullable
    @RequiredReadAction
	private static PyType getGenericConstructorType(PyFunction function, Context context)
	{
		if(PyUtil.isInit(function))
		{
			PyClass cls = function.getContainingClass();
			if(cls != null)
			{
				List<PyGenericType> genericTypes = collectGenericTypes(cls, context);
				List<PyType> elementTypes = new ArrayList<>(genericTypes);
				if(!elementTypes.isEmpty())
				{
					return new PyCollectionTypeImpl(cls, false, elementTypes);
				}
			}
		}
		return null;
	}

	@RequiredReadAction
    private static List<PyGenericType> collectGenericTypes(PyClass cls, Context context)
	{
		boolean isGeneric = false;
		for(PyClass ancestor : cls.getAncestorClasses(context.getTypeContext()))
		{
			if(GENERIC_CLASSES.contains(ancestor.getQualifiedName()))
			{
				isGeneric = true;
				break;
			}
		}
		if(isGeneric)
		{
			List<PyGenericType> results = new ArrayList<>();
			// XXX: Requires switching from stub to AST
			for(PyExpression expr : cls.getSuperClassExpressions())
			{
				if(expr instanceof PySubscriptionExpression subscriptionExpr)
				{
					PyExpression indexExpr = subscriptionExpr.getIndexExpression();
					if(indexExpr != null)
					{
						for(PsiElement resolved : tryResolving(indexExpr, context.getTypeContext()))
						{
							PyGenericType genericType = getGenericType(resolved, context);
							if(genericType != null)
							{
								results.add(genericType);
							}
						}
					}
				}
			}
			return results;
		}
		return Collections.emptyList();
	}

	@Nullable
    @RequiredReadAction
	private static PyType getType(PyExpression expression, Context context)
	{
		List<PyType> members = new ArrayList<>();
		for(PsiElement resolved : tryResolving(expression, context.getTypeContext()))
		{
			members.add(getTypeForResolvedElement(resolved, context));
		}
		return PyUnionType.union(members);
	}

	@Nullable
	private static PyType getTypeForResolvedElement(PsiElement resolved, Context context)
	{
		if(context.getExpressionCache().contains(resolved))
		{
			// Recursive types are not yet supported
			return null;
		}

		context.getExpressionCache().add(resolved);
		try
		{
			PyType unionType = getUnionType(resolved, context);
			if(unionType != null)
			{
				return unionType;
			}
            SimpleReference<PyType> optionalType = getOptionalType(resolved, context);
			if(optionalType != null)
			{
				return optionalType.get();
			}
			PyType callableType = getCallableType(resolved, context);
			if(callableType != null)
			{
				return callableType;
			}
			PyType parameterizedType = getParameterizedType(resolved, context);
			if(parameterizedType != null)
			{
				return parameterizedType;
			}
			PyType builtinCollection = getBuiltinCollection(resolved);
			if(builtinCollection != null)
			{
				return builtinCollection;
			}
			PyType genericType = getGenericType(resolved, context);
			if(genericType != null)
			{
				return genericType;
			}
            SimpleReference<PyType> classType = getClassType(resolved, context.getTypeContext());
			if(classType != null)
			{
				return classType.get();
			}
			PyType stringBasedType = getStringBasedType(resolved, context);
			if(stringBasedType != null)
			{
				return stringBasedType;
			}
			return null;
		}
		finally
		{
			context.getExpressionCache().remove(resolved);
		}
	}

	@Nullable
	public static PyType getType(PsiElement resolved, List<PyType> elementTypes)
	{
		String qualifiedName = getQualifiedName(resolved);

		List<Integer> paramListTypePositions = new ArrayList<>();
		List<Integer> ellipsisTypePositions = new ArrayList<>();
		for(int i = 0; i < elementTypes.size(); i++)
		{
			PyType type = elementTypes.get(i);
			if(type instanceof PyTypeParser.ParameterListType)
			{
				paramListTypePositions.add(i);
			}
			else if(type instanceof PyTypeParser.EllipsisType)
			{
				ellipsisTypePositions.add(i);
			}
		}

		if(!paramListTypePositions.isEmpty())
		{
			if(!("typing.Callable".equals(qualifiedName) && paramListTypePositions.equals(list(0))))
			{
				return null;
			}
		}
		if(!ellipsisTypePositions.isEmpty())
		{
			if(!("typing.Callable".equals(qualifiedName) && ellipsisTypePositions.equals(list(0)) || "typing.Tuple".equals(qualifiedName) && ellipsisTypePositions.equals(list(1)) && elementTypes
					.size() == 2))
			{
				return null;
			}
		}

		if("typing.Union".equals(qualifiedName))
		{
			return PyUnionType.union(elementTypes);
		}
		if("typing.Optional".equals(qualifiedName) && elementTypes.size() == 1)
		{
			return PyUnionType.union(elementTypes.get(0), PyNoneType.INSTANCE);
		}
		if("typing.Callable".equals(qualifiedName) && elementTypes.size() == 2)
		{
			PyTypeParser.ParameterListType paramList = as(elementTypes.get(0), PyTypeParser.ParameterListType.class);
			if(paramList != null)
			{
				return new PyCallableTypeImpl(paramList.getCallableParameters(), elementTypes.get(1));
			}
			if(elementTypes.get(0) instanceof PyTypeParser.EllipsisType)
			{
				return new PyCallableTypeImpl(null, elementTypes.get(1));
			}
		}
		if("typing.Tuple".equals(qualifiedName))
		{
			if(elementTypes.size() > 1 && elementTypes.get(1) instanceof PyTypeParser.EllipsisType)
			{
				return PyTupleType.createHomogeneous(resolved, elementTypes.get(0));
			}
			return PyTupleType.create(resolved, elementTypes);
		}
        if(getBuiltinCollection(resolved) instanceof PyClassType classType)
		{
			return new PyCollectionTypeImpl(classType.getPyClass(), false, elementTypes);
		}
		return null;
	}

	@Nullable
	public static PyType getTypeFromTargetExpression(PyTargetExpression expression, TypeEvalContext context)
	{
		return getTypeFromTargetExpression(expression, new Context(context));
	}

	@Nullable
	private static PyType getTypeFromTargetExpression(PyTargetExpression expression, Context context)
	{
		// XXX: Requires switching from stub to AST
		PyExpression assignedValue = expression.findAssignedValue();
		return assignedValue != null ? getTypeForResolvedElement(assignedValue, context) : null;
	}

	@Nullable
	private static SimpleReference<PyType> getClassType(PsiElement element, TypeEvalContext context)
	{
		if(element instanceof PyTypedElement typedElem)
		{
			PyType type = context.getType(typedElem);
			if(type != null && isAny(type))
			{
				return SimpleReference.create();
			}
			if(type instanceof PyClassLikeType classType)
			{
				if(classType.isDefinition())
				{
					PyType instanceType = classType.toInstance();
					return SimpleReference.create(instanceType);
				}
			}
			else if(type instanceof PyNoneType)
			{
				return SimpleReference.create(type);
			}
		}
		return null;
	}

	@Nullable
    @RequiredReadAction
	private static SimpleReference<PyType> getOptionalType(PsiElement element, Context context)
	{
		if(element instanceof PySubscriptionExpression subscriptionExpr)
		{
			PyExpression operand = subscriptionExpr.getOperand();
			Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
			if(operandNames.contains("typing.Optional"))
			{
				PyExpression indexExpr = subscriptionExpr.getIndexExpression();
				if(indexExpr != null)
				{
					PyType type = getType(indexExpr, context);
					if(type != null)
					{
						return SimpleReference.create(PyUnionType.union(type, PyNoneType.INSTANCE));
					}
				}
				return SimpleReference.create();
			}
		}
		return null;
	}

	@Nullable
    @RequiredReadAction
	private static PyType getStringBasedType(PsiElement element, Context context)
	{
		if(element instanceof PyStringLiteralExpression stringLiteral)
		{
			// XXX: Requires switching from stub to AST
			String contents = stringLiteral.getStringValue();
			return getStringBasedType(contents, element, context);
		}
		return null;
	}

	@Nullable
    @RequiredReadAction
	private static PyType getStringBasedType(String contents, PsiElement anchor, Context context)
	{
		Project project = anchor.getProject();
		PyExpressionCodeFragmentImpl codeFragment = new PyExpressionCodeFragmentImpl(project, "dummy.py", contents, false);
		codeFragment.setContext(anchor.getContainingFile());
        if(codeFragment.getFirstChild() instanceof PyExpressionStatement exprStmt)
		{
			PyExpression expr = exprStmt.getExpression();
			if(expr instanceof PyTupleExpression tupleExpr)
			{
				List<PyType> elementTypes = ContainerUtil.map(tupleExpr.getElements(), elementExpr -> getType(elementExpr, context));
				return PyTupleType.create(anchor, elementTypes);
			}
			return getType(expr, context);
		}
		return null;
	}

	@Nullable
    @RequiredReadAction
	private static PyType getCallableType(PsiElement resolved, Context context)
	{
		if(resolved instanceof PySubscriptionExpression subscriptionExpr)
		{
			PyExpression operand = subscriptionExpr.getOperand();
			Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
            if (operandNames.contains("typing.Callable")
                && subscriptionExpr.getIndexExpression() instanceof PyTupleExpression tupleExpr) {
                PyExpression[] elements = tupleExpr.getElements();
                if (elements.length == 2) {
                    PyExpression parametersExpr = elements[0];
                    PyExpression returnTypeExpr = elements[1];
                    if (parametersExpr instanceof PyListLiteralExpression) {
                        List<PyCallableParameter> parameters = new ArrayList<>();
                        PyListLiteralExpression listExpr = (PyListLiteralExpression) parametersExpr;
                        for (PyExpression argExpr : listExpr.getElements()) {
                            parameters.add(new PyCallableParameterImpl(null, getType(argExpr, context)));
                        }
                        PyType returnType = getType(returnTypeExpr, context);
                        return new PyCallableTypeImpl(parameters, returnType);
                    }
                    if (isEllipsis(parametersExpr)) {
                        return new PyCallableTypeImpl(null, getType(returnTypeExpr, context));
                    }
                }
            }
		}
		return null;
	}

	private static boolean isEllipsis(PyExpression parametersExpr)
	{
		return parametersExpr instanceof PyNoneLiteralExpression && ((PyNoneLiteralExpression) parametersExpr).isEllipsis();
	}

	@Nullable
    @RequiredReadAction
	private static PyType getUnionType(PsiElement element, Context context)
	{
		if(element instanceof PySubscriptionExpression subscriptionExpr)
		{
            Collection<String> operandNames = resolveToQualifiedNames(subscriptionExpr.getOperand(), context.getTypeContext());
			if(operandNames.contains("typing.Union"))
			{
				return PyUnionType.union(getIndexTypes(subscriptionExpr, context));
			}
		}
		return null;
	}

	@Nullable
    @RequiredReadAction
	private static PyGenericType getGenericType(PsiElement element, Context context)
	{
		if(element instanceof PyCallExpression assignedCall)
		{
			PyExpression callee = assignedCall.getCallee();
			if(callee != null)
			{
				Collection<String> calleeQNames = resolveToQualifiedNames(callee, context.getTypeContext());
				if(calleeQNames.contains("typing.TypeVar"))
				{
					PyExpression[] arguments = assignedCall.getArguments();
                    if (arguments.length > 0 && arguments[0] instanceof PyStringLiteralExpression stringLiteral) {
                        String name = stringLiteral.getStringValue();
                        if (name != null) {
                            return new PyGenericType(name, getGenericTypeBound(arguments, context));
                        }
                    }
				}
			}
		}
		return null;
	}

	@Nullable
    @RequiredReadAction
	private static PyType getGenericTypeBound(PyExpression[] typeVarArguments, Context context)
	{
		List<PyType> types = new ArrayList<>();
		for(int i = 1; i < typeVarArguments.length; i++)
		{
			types.add(getType(typeVarArguments[i], context));
		}
		return PyUnionType.union(types);
	}

	@RequiredReadAction
    private static List<PyType> getIndexTypes(PySubscriptionExpression expression, Context context)
	{
		List<PyType> types = new ArrayList<>();
		PyExpression indexExpr = expression.getIndexExpression();
		if(indexExpr instanceof PyTupleExpression tupleExpr)
		{
			for(PyExpression expr : tupleExpr.getElements())
			{
				types.add(getType(expr, context));
			}
		}
		else if(indexExpr != null)
		{
			types.add(getType(indexExpr, context));
		}
		return types;
	}

    @Nullable
    @RequiredReadAction
	private static PyType getParameterizedType(PsiElement element, Context context)
	{
		if(element instanceof PySubscriptionExpression subscriptionExpr)
		{
			PyExpression operand = subscriptionExpr.getOperand();
			PyExpression indexExpr = subscriptionExpr.getIndexExpression();
            if(getType(operand, context) instanceof PyClassType operandClassType)
			{
				PyClass cls = operandClassType.getPyClass();
				List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
				if(PyNames.TUPLE.equals(cls.getQualifiedName()))
				{
					if(indexExpr instanceof PyTupleExpression tupleExpr)
					{
						PyExpression[] elements = tupleExpr.getElements();
						if(elements.length == 2 && isEllipsis(elements[1]))
						{
							return PyTupleType.createHomogeneous(element, indexTypes.get(0));
						}
					}
					return PyTupleType.create(element, indexTypes);
				}
				else if(indexExpr != null)
				{
					return new PyCollectionTypeImpl(cls, false, indexTypes);
				}
			}
		}
		return null;
	}

	@Nullable
	private static PyType getBuiltinCollection(PsiElement element)
	{
		String collectionName = getQualifiedName(element);
		String builtinName = COLLECTION_CLASSES.get(collectionName);
		return builtinName != null ? PyTypeParser.getTypeByName(element, builtinName) : null;
	}

	@RequiredReadAction
    private static List<PsiElement> tryResolving(PyExpression expression, TypeEvalContext context)
	{
		List<PsiElement> elements = Lists.newArrayList();
		if(expression instanceof PyReferenceExpression referenceExpr)
		{
			PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
			PsiPolyVariantReference reference = referenceExpr.getReference(resolveContext);
			List<PsiElement> resolved = PyUtil.multiResolveTopPriority(reference);
			for(PsiElement element : resolved)
			{
				if(element instanceof PyFunction function)
				{
					if(PyUtil.isInit(function))
					{
						PyClass cls = function.getContainingClass();
						if(cls != null)
						{
							elements.add(cls);
							continue;
						}
					}
				}
				else if(element instanceof PyTargetExpression targetExpr)
				{
					// XXX: Requires switching from stub to AST
					PyExpression assignedValue = targetExpr.findAssignedValue();
					if(assignedValue != null)
					{
						elements.add(assignedValue);
						continue;
					}
				}
				if(element != null)
				{
					elements.add(element);
				}
			}
		}
		return !elements.isEmpty() ? elements : Collections.singletonList(expression);
	}

	@RequiredReadAction
    private static Collection<String> resolveToQualifiedNames(PyExpression expression, TypeEvalContext context)
	{
		Set<String> names = Sets.newLinkedHashSet();
		for(PsiElement resolved : tryResolving(expression, context))
		{
			String name = getQualifiedName(resolved);
			if(name != null)
			{
				names.add(name);
			}
		}
		return names;
	}

	@Nullable
	private static String getQualifiedName(PsiElement element)
	{
		if(element instanceof PyQualifiedNameOwner)
		{
			PyQualifiedNameOwner qualifiedNameOwner = (PyQualifiedNameOwner) element;
			return qualifiedNameOwner.getQualifiedName();
		}
		return null;
	}

	private static class Context
	{
		private final TypeEvalContext myContext;
		private final Set<PsiElement> myCache = new HashSet<>();

		private Context(TypeEvalContext context)
		{
			myContext = context;
		}

		public TypeEvalContext getTypeContext()
		{
			return myContext;
		}

		public Set<PsiElement> getExpressionCache()
		{
			return myCache;
		}
	}
}
