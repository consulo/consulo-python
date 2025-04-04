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
package com.jetbrains.python.psi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;

/**
 * Represents a named parameter, as opposed to a tuple parameter.
 */
public interface PyNamedParameter extends PyParameter, PsiNamedElement, PsiNameIdentifierOwner, PyExpression, PyTypeCommentOwner, StubBasedPsiElement<PyNamedParameterStub>
{
	boolean isPositionalContainer();

	boolean isKeywordContainer();

	/**
	 * Parameter is considered "keyword-only" if it appears after named or unnamed positional vararg parameter.
	 * See PEP-3102 for more details.
	 *
	 * @return whether this parameter is keyword-only
	 */
	boolean isKeywordOnly();

	/**
	 * @param includeDefaultValue if true, include the default value after an " = ".
	 * @return Canonical representation of parameter. Includes asterisks for *param and **param, and name.
	 */
	@Nonnull
	String getRepr(boolean includeDefaultValue);

	@Nullable
	PyAnnotation getAnnotation();
}

