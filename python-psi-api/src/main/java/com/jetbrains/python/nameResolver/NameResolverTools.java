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
package com.jetbrains.python.nameResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiCacheKey;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author Ilya.Kazakevich
 */
public final class NameResolverTools
{
	/**
	 * Cache: pair [qualified element name, class name (may be null)] by any psi element.
	 */
	private static final PsiCacheKey<Pair<String, String>, PyElement> QUALIFIED_AND_CLASS_NAME = PsiCacheKey.create(NameResolverTools.class.getName(), new QualifiedAndClassNameObtainer(),
			PsiModificationTracker.MODIFICATION_COUNT);

	private NameResolverTools()
	{

	}

	/**
	 * For each provided element checks if FQ element name is one of provided names
	 *
	 * @param elements       element to check
	 * @param namesProviders some enum that has one or more names
	 * @return true if element's fqn is one of names, provided by provider
	 */
	public static boolean isElementWithName(@Nonnull final Collection<? extends PyElement> elements, @Nonnull final FQNamesProvider... namesProviders)
	{
		for(final PyElement element : elements)
		{
			if(isName(element, namesProviders))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if FQ element name is one of provided names. May be <strong>heavy</strong>.
	 * It is always better to use less accurate but lighter {@link #isCalleeShortCut(PyCallExpression, FQNamesProvider)}
	 *
	 * @param element        element to check
	 * @param namesProviders some enum that has one or more names
	 * @return true if element's fqn is one of names, provided by provider
	 */
	public static boolean isName(@Nonnull final PyElement element, @Nonnull final FQNamesProvider... namesProviders)
	{
		assert element.isValid();
		final Pair<String, String> qualifiedAndClassName = QUALIFIED_AND_CLASS_NAME.getValue(element);
		final String qualifiedName = qualifiedAndClassName.first;
		final String className = qualifiedAndClassName.second;

		for(final FQNamesProvider provider : namesProviders)
		{
			final List<String> names = Arrays.asList(provider.getNames());
			if(qualifiedName != null && names.contains(qualifiedName))
			{
				return true;
			}
			if(className != null && provider.isClass() && names.contains(className))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Looks for parent call of certain function
	 *
	 * @param anchor       element to look parent for
	 * @param functionName function to find
	 * @return parent call or null if not found
	 */
	@Nullable
	public static PyCallExpression findCallExpParent(@Nonnull final PsiElement anchor, @Nonnull final FQNamesProvider functionName)
	{
		final PsiElement parent = PsiTreeUtil.findFirstParent(anchor, new MyFunctionCondition(functionName));
		if(parent instanceof PyCallExpression)
		{
			return (PyCallExpression) parent;
		}
		return null;
	}

	/**
	 * Same as {@link #isName(PyElement, FQNamesProvider...)} for call expr, but first checks name.
	 * Aliases not supported, but much lighter that way
	 *
	 * @param call     expr
	 * @param function names to check
	 * @return true if callee is correct
	 */
	public static boolean isCalleeShortCut(@Nonnull final PyCallExpression call, @Nonnull final FQNamesProvider function)
	{
		final PyExpression callee = call.getCallee();
		if(callee == null)
		{
			return false;
		}

		final String callableName = callee.getName();

		final Collection<String> possibleNames = new LinkedList<>();
		for(final String lastComponent : getLastComponents(function))
		{
			possibleNames.add(lastComponent);
		}
		return possibleNames.contains(callableName) && call.isCallee(function);
	}

	@Nonnull
	private static List<String> getLastComponents(@Nonnull final FQNamesProvider provider)
	{
		final List<String> result = new ArrayList<>();
		for(final String name : provider.getNames())
		{
			final String component = QualifiedName.fromDottedString(name).getLastComponent();
			if(component != null)
			{
				result.add(component);
			}
		}
		return result;
	}

	/**
	 * Checks if some string contains last component one of name
	 *
	 * @param text  test to check
	 * @param names
	 */
	public static boolean isContainsName(@Nonnull final String text, @Nonnull final FQNamesProvider names)
	{
		for(final String lastComponent : getLastComponents(names))
		{
			if(text.contains(lastComponent))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if some file contains last component one of name
	 *
	 * @param file  file to check
	 * @param names
	 */
	public static boolean isContainsName(@Nonnull final PsiFile file, @Nonnull final FQNamesProvider names)
	{
		return isContainsName(file.getText(), names);
	}

	/**
	 * Check if class has parent with some name
	 *
	 * @param child class to check
	 */
	public static boolean isSubclass(@Nonnull final PyClass child, @Nonnull final FQNamesProvider parentName, @Nonnull final TypeEvalContext context)
	{
		for(final String nameToCheck : parentName.getNames())
		{
			if(child.isSubclass(nameToCheck, context))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Looks for call of some function
	 */
	private static class MyFunctionCondition implements Condition<PsiElement>
	{
		@Nonnull
		private final FQNamesProvider myNameToSearch;

		MyFunctionCondition(@Nonnull final FQNamesProvider name)
		{
			myNameToSearch = name;
		}

		@Override
		public boolean value(final PsiElement element)
		{
			if(element instanceof PyCallExpression)
			{
				return ((PyCallExpression) element).isCallee(myNameToSearch);
			}
			return false;
		}
	}

	/**
	 * Returns pair [qualified name, class name (may be null)] by psi element
	 */
	private static class QualifiedAndClassNameObtainer implements Function<PyElement, Pair<String, String>>
	{
		@Override
		@Nonnull
		public Pair<String, String> fun(@Nonnull final PyElement param)
		{
			PyElement elementToCheck = param;

			// Trying to use no implicit context if possible...
			final PsiReference reference;
			if(param instanceof PyReferenceOwner)
			{
				reference = ((PyReferenceOwner) param).getReference(PyResolveContext.noImplicits());
			}
			else
			{
				reference = param.getReference();
			}

			if(reference != null)
			{
				final PsiElement resolvedElement = reference.resolve();
				if(resolvedElement instanceof PyElement)
				{
					elementToCheck = (PyElement) resolvedElement;
				}
			}
			String qualifiedName = null;
			if(elementToCheck instanceof PyQualifiedNameOwner)
			{
				qualifiedName = ((PyQualifiedNameOwner) elementToCheck).getQualifiedName();
			}
			String className = null;
			if(elementToCheck instanceof PyFunction)
			{
				final PyClass aClass = ((PyFunction) elementToCheck).getContainingClass();
				if(aClass != null)
				{
					className = aClass.getQualifiedName();
				}
			}
			return Pair.create(qualifiedName, className);
		}
	}
}
