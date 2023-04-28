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
package com.jetbrains.python.codeInsight.testIntegration;

import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.Lists;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.undoRedo.CommandProcessor;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.pytest.PyTestUtil;

/**
 * User: catherine
 */
public class CreateTestAction extends PsiElementBaseIntentionAction
{
	@Nonnull
	public String getFamilyName()
	{
		return CodeInsightBundle.message("intention.create.test");
	}


	public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element)
	{
		PyClass psiClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

		if(psiClass != null && PyTestUtil.isPyTestClass(psiClass, null))
		{
			return false;
		}
		return true;
	}

	@Override
	public void invoke(final @Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException
	{
		final PyFunction srcFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
		final PyClass srcClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

		if(srcClass == null && srcFunction == null)
		{
			return;
		}

		final PsiDirectory dir = element.getContainingFile().getContainingDirectory();
		final CreateTestDialog d = new CreateTestDialog(project);
		if(srcClass != null)
		{
			d.setClassName("Test" + StringUtil.capitalize(srcClass.getName()));
			d.setFileName("test_" + StringUtil.decapitalize(srcClass.getName()) + ".py");

			if(dir != null)
			{
				d.setTargetDir(dir.getVirtualFile().getPath());
			}

			if(srcFunction != null)
			{
				d.methodsSize(1);
				d.addMethod("test_" + srcFunction.getName(), 0);
			}
			else
			{
				final List<PyFunction> methods = Lists.newArrayList();
				srcClass.visitMethods(pyFunction -> {
					if(pyFunction.getName() != null && !pyFunction.getName().startsWith("__"))
					{
						methods.add(pyFunction);
					}
					return true;
				}, false, null);

				d.methodsSize(methods.size());
				int i = 0;
				for(PyFunction f : methods)
				{
					d.addMethod("test_" + f.getName(), i);
					++i;
				}
			}
		}
		else
		{
			d.setClassName("Test" + StringUtil.capitalize(srcFunction.getName()));
			d.setFileName("test_" + StringUtil.decapitalize(srcFunction.getName()) + ".py");
			if(dir != null)
			{
				d.setTargetDir(dir.getVirtualFile().getPath());
			}

			d.methodsSize(1);
			d.addMethod("test_" + srcFunction.getName(), 0);
		}

		if(!d.showAndGet())
		{
			return;
		}
		CommandProcessor.getInstance().executeCommand(project, () -> {
			PsiFile e = PyTestCreator.generateTestAndNavigate(project, d);
			final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
			documentManager.commitAllDocuments();
		}, CodeInsightBundle.message("intention.create.test"), this);
	}
}