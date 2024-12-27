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
package com.jetbrains.python.impl.codeInsight.dataflow.scope;

import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.language.dataFlow.DFALimitExceededException;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * @author oleg
 */
public interface Scope
{
	/*
	 * @return defined scope local/instance/class variables and parameters, using reaching defs
	 */
	@Nullable
	ScopeVariable getDeclaredVariable(@Nonnull PsiElement anchorElement, @Nonnull String name) throws DFALimitExceededException;

	boolean isGlobal(String name);

	boolean isNonlocal(String name);

	boolean containsDeclaration(String name);

	@Nonnull
	List<PyImportedNameDefiner> getImportedNameDefiners();

	@Nonnull
	Collection<PsiNamedElement> getNamedElements(String name, boolean includeNestedGlobals);

	@Nonnull
	Collection<PsiNamedElement> getNamedElements();

	@Nonnull
	Collection<PyTargetExpression> getTargetExpressions();
}
