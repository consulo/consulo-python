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

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.testIntegration.TestCreator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * User: catherine
 */
@ExtensionImpl
public class PyTestCreator implements TestCreator
{
	private static final Logger LOG = Logger.getInstance(PyTestCreator.class);

	@Override
	public boolean isAvailable(Project project, Editor editor, PsiFile file)
	{
		CreateTestAction action = new CreateTestAction();
		PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
		if(element != null)
		{
			return action.isAvailable(project, editor, element);
		}
		return false;
	}

	@Override
	public void createTest(Project project, Editor editor, PsiFile file)
	{
		try
		{
			CreateTestAction action = new CreateTestAction();
			PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
			if(action.isAvailable(project, editor, element))
			{
				action.invoke(project, editor, file.getContainingFile());
			}
		}
		catch(IncorrectOperationException e)
		{
			LOG.warn(e);
		}
	}

	/**
	 * Generates test, puts it into file and navigates to newly created class
	 *
	 * @return file with test
	 */
	static PsiFile generateTestAndNavigate(@Nonnull final Project project, @Nonnull final CreateTestDialog dialog)
	{
		return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> ApplicationManager.getApplication().runWriteAction(new Computable<PsiFile>()
		{
			public PsiFile compute()
			{
				try
				{
					final PyElement testClass =
							generateTest(project, dialog);
					testClass.navigate(false);
					return testClass.getContainingFile();
				}
				catch(IncorrectOperationException e)
				{
					LOG.warn(e);
					return null;
				}
			}
		}));
	}

	/**
	 * Generates test, puts it into file and returns class element for test
	 *
	 * @return newly created test class
	 */
	@Nonnull
	static PyElement generateTest(@Nonnull final Project project, @Nonnull final CreateTestDialog dialog)
	{
		IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

		String fileName = dialog.getFileName();
		if(!fileName.endsWith(".py"))
		{
			fileName = fileName + "." + PythonFileType.INSTANCE.getDefaultExtension();
		}

		StringBuilder fileText = new StringBuilder();
		fileText.append("class ").append(dialog.getClassName()).append("(TestCase):\n\t");
		List<String> methods = dialog.getMethods();
		if(methods.size() == 0)
		{
			fileText.append("pass\n");
		}

		for(String method : methods)
		{
			fileText.append("def ").append(method).append("(self):\n\tself.fail()\n\n\t");
		}

		PsiFile psiFile = PyUtil.getOrCreateFile(dialog.getTargetDir() + "/" + fileName, project);
		AddImportHelper.addOrUpdateFromImportStatement(psiFile, "unittest", "TestCase", null, AddImportHelper.ImportPriority.BUILTIN, null);

		PyElement createdClass =
				PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(psiFile), PyClass.class, fileText.toString());
		createdClass = (PyElement) psiFile.addAfter(createdClass, psiFile.getLastChild());

		PostprocessReformattingAspect.getInstance(project).doPostponedFormatting(psiFile.getViewProvider());
		CodeStyleManager.getInstance(project).reformat(psiFile);
		return createdClass;
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
