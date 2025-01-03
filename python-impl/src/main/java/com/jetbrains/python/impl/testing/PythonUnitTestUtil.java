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
package com.jetbrains.python.impl.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.util.collection.Stack;
import com.jetbrains.python.psi.PyAssertStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;


/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestUtil
{
	public static final String TESTCASE_SETUP_NAME = "setUp";
	private static final HashSet<String> PYTHON_TEST_QUALIFIED_CLASSES = Sets.newHashSet("unittest.TestCase", "unittest.case.TestCase");
	private static final Pattern TEST_MATCH_PATTERN = Pattern.compile("(?:^|[\b_\\.%s-])[Tt]est");
	private static final String TESTCASE_METHOD_PREFIX = "test";

	private PythonUnitTestUtil()
	{
	}

	public static boolean isUnitTestCaseFunction(PyFunction function)
	{
		final String name = function.getName();
		if(name == null || !name.startsWith(TESTCASE_METHOD_PREFIX))
		{
			return false;
		}

		final PyClass containingClass = function.getContainingClass();
		if(containingClass == null || !isUnitTestCaseClass(containingClass, PYTHON_TEST_QUALIFIED_CLASSES))
		{
			return false;
		}

		return true;
	}

	public static boolean isUnitTestCaseClass(PyClass cls)
	{
		return isUnitTestCaseClass(cls, PYTHON_TEST_QUALIFIED_CLASSES);
	}

	public static boolean isUnitTestFile(PyFile file)
	{
		if(!file.getName().startsWith("test"))
		{
			return false;
		}
		return true;
	}

	private static boolean isUnitTestCaseClass(PyClass cls, HashSet<String> testQualifiedNames)
	{
		if(ApplicationManager.getApplication().isUnitTestMode())
		{
			for(PyExpression expression : cls.getSuperClassExpressions())
			{
				if(expression.getText().equals("TestCase"))
				{
					return true;
				}
			}
		}
		for(PyClassLikeType type : cls.getAncestorTypes(TypeEvalContext.codeInsightFallback(cls.getProject())))
		{
			if(type != null && testQualifiedNames.contains(type.getClassQName()))
			{
				return true;
			}
		}
		return false;
	}

	public static List<PyStatement> getTestCaseClassesFromFile(PsiFile file, @Nullable final TypeEvalContext context)
	{
		if(file instanceof PyFile)
		{
			return getTestCaseClassesFromFile((PyFile) file, PYTHON_TEST_QUALIFIED_CLASSES, context);
		}
		return Collections.emptyList();
	}

	public static List<PyStatement> getTestCaseClassesFromFile(PyFile file, Set<String> testQualifiedNames, @Nullable final TypeEvalContext context)
	{
		List<PyStatement> result = Lists.newArrayList();
		for(PyClass cls : file.getTopLevelClasses())
		{
			if(isTestCaseClassWithContext(cls, testQualifiedNames, context))
			{
				result.add(cls);
			}
		}
		for(PyFunction cls : file.getTopLevelFunctions())
		{
			if(isTestCaseFunction(cls, false))
			{
				result.add(cls);
			}
		}
		return result;
	}

	public static boolean isTestCaseFunction(PyFunction function)
	{
		return isTestCaseFunction(function, true);
	}

	public static boolean isTestCaseFunction(PyFunction function, boolean checkAssert)
	{
		final String name = function.getName();
		if(name == null || !TEST_MATCH_PATTERN.matcher(name).find())
		{
			return false;
		}
		if(function.getContainingClass() != null)
		{
			if(isTestCaseClass(function.getContainingClass(), null))
			{
				return true;
			}
		}
		if(checkAssert)
		{
			boolean hasAssert = hasAssertOrYield(function.getStatementList());
			if(hasAssert)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasAssertOrYield(PyStatementList list)
	{
		Stack<PsiElement> stack = new Stack<>();
		if(list != null)
		{
			for(PyStatement st : list.getStatements())
			{
				stack.push(st);
				while(!stack.isEmpty())
				{
					PsiElement e = stack.pop();
					if(e instanceof PyAssertStatement || e instanceof PyYieldExpression)
					{
						return true;
					}
					for(PsiElement psiElement : e.getChildren())
					{
						stack.push(psiElement);
					}
				}
			}
		}
		return false;
	}

	public static boolean isTestCaseClass(@Nonnull PyClass cls, @Nullable final TypeEvalContext context)
	{
		return isTestCaseClassWithContext(cls, PYTHON_TEST_QUALIFIED_CLASSES, context);
	}

	public static boolean isTestCaseClassWithContext(@Nonnull PyClass cls, Set<String> testQualifiedNames, @Nullable TypeEvalContext context)
	{
		final TypeEvalContext contextToUse = (context != null ? context : TypeEvalContext.codeInsightFallback(cls.getProject()));
		for(PyClassLikeType type : cls.getAncestorTypes(contextToUse))
		{
			if(type != null)
			{
				if(testQualifiedNames.contains(type.getClassQName()))
				{
					return true;
				}
				String clsName = cls.getQualifiedName();
				String[] names = new String[0];
				if(clsName != null)
				{
					names = clsName.split("\\.");
				}
				if(names.length == 0)
				{
					return false;
				}

				clsName = names[names.length - 1];
				if(TEST_MATCH_PATTERN.matcher(clsName).find())
				{
					return true;
				}
			}
		}
		return false;
	}

	public static List<Location> findLocations(@Nonnull final Project project, @Nonnull String fileName, @Nullable String className, @Nullable String methodName)
	{
		if(fileName.contains("%"))
		{
			fileName = fileName.substring(0, fileName.lastIndexOf("%"));
		}
		final List<Location> locations = new ArrayList<>();
		if(methodName == null && className == null)
		{
			final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
			if(virtualFile == null)
			{
				return locations;
			}
			final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
			if(psiFile != null)
			{
				locations.add(new PsiLocation<>(project, psiFile));
			}
		}

		if(className != null)
		{
			for(PyClass cls : PyClassNameIndex.find(className, project, false))
			{
				ProgressManager.checkCanceled();

				final PsiFile containingFile = cls.getContainingFile();
				final VirtualFile virtualFile = containingFile.getVirtualFile();
				final String clsFileName = virtualFile == null ? containingFile.getName() : virtualFile.getPath();
				final String clsFileNameWithoutExt = FileUtil.getNameWithoutExtension(clsFileName);
				if(!clsFileNameWithoutExt.endsWith(fileName) && !fileName.equals(clsFileName))
				{
					continue;
				}
				if(methodName == null)
				{
					locations.add(new PsiLocation<>(project, cls));
				}
				else
				{
					final PyFunction method = cls.findMethodByName(methodName, true, null);
					if(method == null)
					{
						continue;
					}

					locations.add(new PyPsiLocationWithFixedClass(project, method, cls));
				}
			}
		}
		else if(methodName != null)
		{
			for(PyFunction function : PyFunctionNameIndex.find(methodName, project))
			{
				ProgressManager.checkCanceled();
				if(function.getContainingClass() == null)
				{
					final PsiFile containingFile = function.getContainingFile();
					final VirtualFile virtualFile = containingFile.getVirtualFile();
					final String clsFileName = virtualFile == null ? containingFile.getName() : virtualFile.getPath();
					final String clsFileNameWithoutExt = FileUtil.getNameWithoutExtension(clsFileName);
					if(!clsFileNameWithoutExt.endsWith(fileName))
					{
						continue;
					}
					locations.add(new PsiLocation<>(project, function));
				}
			}
		}
		return locations;
	}
}
