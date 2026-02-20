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
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import consulo.util.lang.Pair;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;

/**
 * Plugin that moves class methods
 *
 * @author Ilya.Kazakevich
 */
class MethodsManager extends MembersManager<PyFunction>
{

	/**
	 * Some decorators should be copied with methods if method is marked abstract. Here is list.
	 */
	private static final String[] DECORATORS_MAY_BE_COPIED_TO_ABSTRACT = {
			PyNames.PROPERTY,
			PyNames.CLASSMETHOD,
			PyNames.STATICMETHOD
	};

	public static final String ABC_META_PACKAGE = "abc";
	private static final NoPropertiesPredicate NO_PROPERTIES = new NoPropertiesPredicate();

	MethodsManager()
	{
		super(PyFunction.class);
	}

	@Override
	public boolean hasConflict(@Nonnull PyFunction member, @Nonnull PyClass aClass)
	{
		return NamePredicate.hasElementWithSameName(member, Arrays.asList(aClass.getMethods()));
	}

	@Nonnull
	@Override
	protected Collection<PyElement> getDependencies(@Nonnull MultiMap<PyClass, PyElement> usedElements)
	{
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	protected MultiMap<PyClass, PyElement> getDependencies(@Nonnull PyElement member)
	{
		MyPyRecursiveElementVisitor visitor = new MyPyRecursiveElementVisitor();
		member.accept(visitor);
		return visitor.myResult;
	}

	@Nonnull
	@Override
	protected List<? extends PyElement> getMembersCouldBeMoved(@Nonnull PyClass pyClass)
	{
		return FluentIterable.from(Arrays.asList(pyClass.getMethods())).filter(new NamelessFilter<>()).filter(NO_PROPERTIES).toList();
	}

	@Override
	protected Collection<PyElement> moveMembers(@Nonnull PyClass from, @Nonnull Collection<PyMemberInfo<PyFunction>> members, @Nonnull PyClass... to)
	{
		Collection<PyFunction> methodsToMove = fetchElements(Collections2.filter(members, new AbstractFilter(false)));
		Collection<PyFunction> methodsToAbstract = fetchElements(Collections2.filter(members, new AbstractFilter(true)));

		makeMethodsAbstract(methodsToAbstract, to);
		return moveMethods(from, methodsToMove, true, to);
	}

	/**
	 * Creates abstract version of each method in each class (does not touch method itself as opposite to {@link #moveMethods(com.jetbrains.python.psi.PyClass, java.util.Collection,
	 * com.jetbrains.python.psi.PyClass...)})
	 *
	 * @param currentFunctions functions to make them abstract
	 * @param to               classes where abstract method should be created
	 */
	private static void makeMethodsAbstract(Collection<PyFunction> currentFunctions, PyClass... to)
	{
		Set<PsiFile> filesToCheckImport = new HashSet<>();
		Set<PyClass> classesToAddMetaAbc = new HashSet<>();

		for(PyFunction function : currentFunctions)
		{
			for(PyClass destClass : to)
			{
				PyFunctionBuilder functionBuilder = PyFunctionBuilder.copySignature(function, DECORATORS_MAY_BE_COPIED_TO_ABSTRACT);
				functionBuilder.decorate(PyNames.ABSTRACTMETHOD);
				LanguageLevel level = LanguageLevel.forElement(destClass);
				PyClassRefactoringUtil.addMethods(destClass, false, functionBuilder.buildFunction(destClass.getProject(), level));
				classesToAddMetaAbc.add(destClass);
			}
		}

		// Add ABCMeta to new classes if needed
		for(PyClass aClass : classesToAddMetaAbc)
		{
			if(addMetaAbcIfNeeded(aClass))
			{
				filesToCheckImport.add(aClass.getContainingFile());
			}
		}

		// Add imports for ABC if needed
		for(PsiFile file : filesToCheckImport)
		{
			addImportFromAbc(file, PyNames.ABSTRACTMETHOD);
			addImportFromAbc(file, PyNames.ABC_META_CLASS);
			PyClassRefactoringUtil.optimizeImports(file); //To remove redundant imports
		}
	}

	/**
	 * Adds metaclass = ABCMeta for class if has no.
	 *
	 * @param aClass class where it should be added
	 * @return true if added. False if class already has metaclass so we did not touch it.
	 */
	// TODO: Copy/Paste with PyClass.getMeta..
	private static boolean addMetaAbcIfNeeded(@Nonnull PyClass aClass)
	{
		PsiFile file = aClass.getContainingFile();
		PyType type = aClass.getMetaClassType(TypeEvalContext.userInitiated(aClass.getProject(), file));
		if(type != null)
		{
			return false; //User already has metaclass. He probably knows about metaclasses, so we should not add ABCMeta
		}
		LanguageLevel languageLevel = LanguageLevel.forElement(aClass);
		if(languageLevel.isPy3K())
		{ //TODO: Copy/paste, use strategy because we already has the same check in #couldBeAbstract
			// Add (metaclass= for Py3K
			PyClassRefactoringUtil.addSuperClassExpressions(aClass.getProject(), aClass, null, Collections.singletonList(Pair.create(PyNames.METACLASS, PyNames.ABC_META_CLASS)));
		}
		else
		{
			// Add __metaclass__ for Py2
			PyClassRefactoringUtil.addClassAttributeIfNotExist(aClass, PyNames.DUNDER_METACLASS, PyNames.ABC_META_CLASS);
		}
		return true;
	}

	/**
	 * Adds import from ABC module
	 *
	 * @param file         where to add import
	 * @param nameToImport what to import
	 */
	private static void addImportFromAbc(@Nonnull PsiFile file, @Nonnull String nameToImport)
	{
		AddImportHelper.addOrUpdateFromImportStatement(file, ABC_META_PACKAGE, nameToImport, null, ImportPriority.BUILTIN, null);
	}

	/**
	 * Moves methods (as opposite to {@link #makeMethodsAbstract(java.util.Collection, com.jetbrains.python.psi.PyClass...)})
	 *
	 * @param from          source
	 * @param methodsToMove what to move
	 * @param to            where
	 * @param skipIfExist   skip (do not add) if method already exists
	 * @return newly added methods
	 */
	static List<PyElement> moveMethods(PyClass from, Collection<PyFunction> methodsToMove, boolean skipIfExist, PyClass... to)
	{
		List<PyElement> result = new ArrayList<>();
		for(PyClass destClass : to)
		{
			//We move copies here because there may be several destinations
			List<PyFunction> copies = new ArrayList<>(methodsToMove.size());
			for(PyFunction element : methodsToMove)
			{
				PyFunction newMethod = (PyFunction) element.copy();
				copies.add(newMethod);
			}

			result.addAll(PyClassRefactoringUtil.copyMethods(copies, destClass, skipIfExist));
		}
		deleteElements(methodsToMove);

		return result;
	}

	@Nonnull
	@Override
	public PyMemberInfo<PyFunction> apply(@Nonnull PyFunction pyFunction)
	{
		PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(pyFunction);
		assert flags != null : "No flags return while element is function " + pyFunction;
		boolean isStatic = flags.isStaticMethod() || flags.isClassMethod();
		return new PyMemberInfo<>(pyFunction, isStatic, buildDisplayMethodName(pyFunction), isOverrides(pyFunction), this, couldBeAbstract(pyFunction));
	}

	/**
	 * @return if method could be made abstract? (that means "create abstract version if method in parent class")
	 */
	private static boolean couldBeAbstract(@Nonnull PyFunction function)
	{
		if(PyUtil.isInit(function))
		{
			return false; // Who wants to make __init__ abstract?!
		}
		PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(function);
		assert flags != null : "Function should be called on method!";

		boolean py3K = LanguageLevel.forElement(function).isPy3K();

		//TODO: use strategy because we already has the same check in #addMetaAbcIfNeeded
		return flags.isInstanceMethod() || py3K; //Any method could be made abstract in py3
	}


	@Nullable
	private static Boolean isOverrides(PyFunction pyFunction)
	{
		PyClass clazz = PyUtil.getContainingClassOrSelf(pyFunction);
		assert clazz != null : "Refactoring called on function, not method: " + pyFunction;
		for(PyClass parentClass : clazz.getSuperClasses(null))
		{
			PyFunction parentMethod = parentClass.findMethodByName(pyFunction.getName(), true, null);
			if(parentMethod != null)
			{
				return true;
			}
		}
		return null;
	}

	@Nonnull
	private static String buildDisplayMethodName(@Nonnull PyFunction pyFunction)
	{
		StringBuilder builder = new StringBuilder(pyFunction.getName());
		builder.append('(');
		PyParameter[] arguments = pyFunction.getParameterList().getParameters();
		for(PyParameter parameter : arguments)
		{
			builder.append(parameter.getName());
			if(arguments.length > 1 && parameter != arguments[arguments.length - 1])
			{
				builder.append(", ");
			}
		}
		builder.append(')');
		return builder.toString();
	}


	/**
	 * Filters member infos to find if they should be abstracted
	 */
	private static class AbstractFilter extends NotNullPredicate<PyMemberInfo<PyFunction>>
	{
		private final boolean myAllowAbstractOnly;

		/**
		 * @param allowAbstractOnly returns only methods to be abstracted. Returns only methods to be moved otherwise.
		 */
		private AbstractFilter(boolean allowAbstractOnly)
		{
			myAllowAbstractOnly = allowAbstractOnly;
		}

		@Override
		protected boolean applyNotNull(@Nonnull PyMemberInfo<PyFunction> input)
		{
			return input.isToAbstract() == myAllowAbstractOnly;
		}
	}

	private static class MyPyRecursiveElementVisitor extends PyRecursiveElementVisitorWithResult
	{
		@Override
		public void visitPyCallExpression(PyCallExpression node)
		{
			// TODO: refactor, messy code
			PyExpression callee = node.getCallee();
			if(callee != null)
			{
				PsiReference calleeRef = callee.getReference();
				if(calleeRef != null)
				{
					PsiElement calleeDeclaration = calleeRef.resolve();
					if(calleeDeclaration instanceof PyFunction)
					{
						PyFunction calleeFunction = (PyFunction) calleeDeclaration;
						PyClass clazz = calleeFunction.getContainingClass();
						if(clazz != null)
						{
							if(PyUtil.isInit(calleeFunction))
							{
								return; // Init call should not be marked as dependency
							}
							myResult.putValue(clazz, calleeFunction);
						}
					}
				}
			}
		}
	}

	/**
	 * Filter out property setters and getters
	 */
	private static class NoPropertiesPredicate implements Predicate<PyFunction>
	{
		@Override
		public boolean apply(@Nonnull PyFunction input)
		{
			return input.getProperty() == null;
		}
	}
}
