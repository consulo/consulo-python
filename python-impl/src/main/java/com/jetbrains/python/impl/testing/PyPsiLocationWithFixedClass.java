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
package com.jetbrains.python.impl.testing;

import jakarta.annotation.Nonnull;

import consulo.execution.action.PsiLocation;
import consulo.project.Project;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;

/**
 * PSI-based location may point to function, but function may be situated in abstract class.
 * So, we need point to real class as well.
 *
 * @author Ilya.Kazakevich
 */
public final class PyPsiLocationWithFixedClass extends PsiLocation<PyFunction>
{
	@Nonnull
	private final PyClass myFixedClass;

	PyPsiLocationWithFixedClass(@Nonnull final Project project, @Nonnull final PyFunction psiElement, @Nonnull final PyClass fixedClass)
	{
		super(project, psiElement);
		myFixedClass = fixedClass;
	}

	@Nonnull
	public PyClass getFixedClass()
	{
		return myFixedClass;
	}
}
