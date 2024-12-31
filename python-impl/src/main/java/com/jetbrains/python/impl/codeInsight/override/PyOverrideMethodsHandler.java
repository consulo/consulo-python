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
package com.jetbrains.python.impl.codeInsight.override;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.generation.OverrideMethodHandler;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyOverrideMethodsHandler implements OverrideMethodHandler
{
	public boolean isValidFor(Editor editor, PsiFile file)
	{
		return (file instanceof PyFile) && (PyOverrideImplementUtil.getContextClass(editor, file) != null);
	}

	public void invoke(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file)
	{
		PyClass aClass = PyOverrideImplementUtil.getContextClass(editor, file);
		if(aClass != null)
		{
			PyOverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
		}
	}

	public boolean startInWriteAction()
	{
		return false;
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
