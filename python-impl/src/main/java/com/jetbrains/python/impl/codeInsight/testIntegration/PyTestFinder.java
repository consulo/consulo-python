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
package com.jetbrains.python.impl.codeInsight.testIntegration;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.impl.testing.PythonUnitTestUtil;
import com.jetbrains.python.impl.testing.doctest.PythonDocTestUtil;
import com.jetbrains.python.impl.testing.pytest.PyTestUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.testIntegration.TestFinder;
import consulo.language.editor.testIntegration.TestFinderHelper;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User : catherine
 */
@ExtensionImpl
public class PyTestFinder implements TestFinder
{
	public PyDocStringOwner findSourceElement(@Nonnull PsiElement element)
	{
		return PsiTreeUtil.getParentOfType(element, PyClass.class, PyFunction.class);
	}

	@Nonnull
	@Override
	public Collection<PsiElement> findTestsForClass(@Nonnull PsiElement element)
	{
		PyDocStringOwner source = findSourceElement(element);
		if(source == null)
		{
			return Collections.emptySet();
		}

		String sourceName = source.getName();
		if(sourceName == null)
		{
			return Collections.emptySet();
		}
		List<Pair<? extends PsiNamedElement, Integer>> classesWithProximities = new ArrayList<>();

		if(source instanceof PyClass)
		{
			Collection<String> names = PyClassNameIndex.allKeys(element.getProject());
			for(String eachName : names)
			{
				if(eachName.contains(sourceName))
				{
					for(PyClass eachClass : PyClassNameIndex.find(eachName, element.getProject(), GlobalSearchScope.projectScope(element.getProject())))
					{
						if(PythonUnitTestUtil.isTestCaseClass(eachClass, null) || PythonDocTestUtil.isDocTestClass(eachClass))
						{
							classesWithProximities.add(new Pair<PsiNamedElement, Integer>(eachClass, TestFinderHelper.calcTestNameProximity(sourceName, eachName)));
						}
					}
				}
			}
		}
		else
		{
			Collection<String> names = PyFunctionNameIndex.allKeys(element.getProject());
			for(String eachName : names)
			{
				if(eachName.contains(sourceName))
				{
					for(PyFunction eachFunction : PyFunctionNameIndex.find(eachName, element.getProject(), GlobalSearchScope.projectScope(element.getProject())))
					{
						if(PythonUnitTestUtil.isTestCaseFunction(eachFunction) || PythonDocTestUtil.isDocTestFunction(eachFunction))
						{
							classesWithProximities.add(new Pair<PsiNamedElement, Integer>(eachFunction, TestFinderHelper.calcTestNameProximity(sourceName, eachName)));
						}
					}
				}
			}
		}
		return TestFinderHelper.getSortedElements(classesWithProximities, true);
	}

	@Nonnull
	@Override
	public Collection<PsiElement> findClassesForTest(@Nonnull PsiElement element)
	{
		PyFunction sourceFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
		PyClass source = PsiTreeUtil.getParentOfType(element, PyClass.class);
		if(sourceFunction == null && source == null)
		{
			return Collections.emptySet();
		}

		List<Pair<? extends PsiNamedElement, Integer>> classesWithWeights = new ArrayList<>();
		List<Pair<String, Integer>> possibleNames = new ArrayList<>();
		if(source != null)
		{
			possibleNames.addAll(TestFinderHelper.collectPossibleClassNamesWithWeights(source.getName()));
		}
		if(sourceFunction != null)
		{
			possibleNames.addAll(TestFinderHelper.collectPossibleClassNamesWithWeights(sourceFunction.getName()));
		}

		for(Pair<String, Integer> eachNameWithWeight : possibleNames)
		{
			for(PyClass eachClass : PyClassNameIndex.find(eachNameWithWeight.first, element.getProject(), GlobalSearchScope.projectScope(element.getProject())))
			{
				if(!PyTestUtil.isPyTestClass(eachClass, null))
				{
					classesWithWeights.add(new Pair<PsiNamedElement, Integer>(eachClass, eachNameWithWeight.second));
				}
			}
			for(PyFunction function : PyFunctionNameIndex.find(eachNameWithWeight.first, element.getProject(), GlobalSearchScope.projectScope(element.getProject())))
			{
				if(!PyTestUtil.isPyTestFunction(function))
				{
					classesWithWeights.add(new Pair<PsiNamedElement, Integer>(function, eachNameWithWeight.second));
				}
			}

		}
		return TestFinderHelper.getSortedElements(classesWithWeights, false);
	}

	@Override
	public boolean isTest(@Nonnull PsiElement element)
	{
		PyClass cl = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
		if(cl != null)
		{
			return PyTestUtil.isPyTestClass(cl, null);
		}
		return false;
	}
}
