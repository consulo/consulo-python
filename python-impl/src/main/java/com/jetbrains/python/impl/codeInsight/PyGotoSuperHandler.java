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
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.language.Language;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.action.GotoSuperActionHander;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.ex.popup.JBPopup;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyGotoSuperHandler implements GotoSuperActionHander
{
	public void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile file)
	{
		PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
		PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
		if(pyClass != null)
		{
			PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class, false, PyClass.class);
			if(function != null)
			{
				gotoSuperFunctions(editor, function, pyClass);
			}
			else
			{
				final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class, false, PyClass.class);
				if(assignment != null && assignment.getTargets()[0] instanceof PyTargetExpression)
				{
					gotoSuperClassAttributes(editor, (PyTargetExpression) assignment.getTargets()[0], pyClass);
				}
				else
				{
					navigateOrChoose(editor, PyUtil.getAllSuperClasses(pyClass), "Choose superclass");
				}
			}
		}
	}

	private static void gotoSuperFunctions(Editor editor, PyFunction function, PyClass pyClass)
	{
		final Collection<PyFunction> superFunctions = getAllSuperMethodsByName(function, pyClass);
		navigateOrChoose(editor, superFunctions, CodeInsightBundle.message("goto.super.method.chooser.title"));
	}

	private static void gotoSuperClassAttributes(Editor editor, PyTargetExpression attr, PyClass pyClass)
	{
		final Collection<PyTargetExpression> attrs = getAllSuperAttributesByName(attr, pyClass);
		navigateOrChoose(editor, attrs, "Choose superclass attribute");
	}

	private static void navigateOrChoose(Editor editor, Collection<? extends NavigatablePsiElement> superElements, final String title)
	{
		if(!superElements.isEmpty())
		{
			NavigatablePsiElement[] superElementArray = superElements.toArray(new NavigatablePsiElement[superElements.size()]);
			if(superElementArray.length == 1)
			{
				superElementArray[0].navigate(true);
			}
			else
			{
				JBPopup popup = PopupNavigationUtil.getPsiElementPopup(superElementArray, title);
				EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
			}
		}
	}

	private static Collection<PyTargetExpression> getAllSuperAttributesByName(@Nonnull final PyTargetExpression classAttr, PyClass pyClass)
	{
		final String name = classAttr.getName();
		if(name == null)
		{
			return Collections.emptyList();
		}
		final List<PyTargetExpression> result = new ArrayList<>();
		for(PyClass aClass : pyClass.getAncestorClasses(null))
		{
			final PyTargetExpression superAttr = aClass.findClassAttribute(name, false, null);
			if(superAttr != null)
			{
				result.add(superAttr);
			}
		}
		return result;
	}

	private static Collection<PyFunction> getAllSuperMethodsByName(@Nonnull final PyFunction method, PyClass pyClass)
	{
		final String name = method.getName();
		if(name == null)
		{
			return Collections.emptyList();
		}
		final List<PyFunction> result = new ArrayList<>();
		for(PyClass aClass : pyClass.getAncestorClasses(null))
		{
			final PyFunction byName = aClass.findMethodByName(name, false, null);
			if(byName != null)
			{
				result.add(byName);
			}
		}
		return result;
	}

	public boolean startInWriteAction()
	{
		return false;
	}

	@Override
	public boolean isValidFor(Editor editor, PsiFile file)
	{
		return true;
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
