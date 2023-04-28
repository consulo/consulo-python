/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.ide.navigation.GotoSymbolContributor;
import consulo.language.psi.search.FindSymbolParameters;
import consulo.language.psi.stub.IdFilter;
import consulo.language.psi.stub.StubIndex;
import consulo.navigation.NavigationItem;
import consulo.project.content.scope.ProjectAwareSearchScope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyGotoSymbolContributor implements GotoSymbolContributor
{
	@Override
	public void processNames(@Nonnull Processor<String> processor, @Nonnull SearchScope searchScope, @Nullable IdFilter idFilter)
	{
		ProjectAwareSearchScope projectAwareSearchScope = (ProjectAwareSearchScope) searchScope;

		StubIndex.getInstance().processAllKeys(PyClassNameIndex.KEY, processor, projectAwareSearchScope, idFilter);
		StubIndex.getInstance().processAllKeys(PyFunctionNameIndex.KEY, processor, projectAwareSearchScope, idFilter);
		StubIndex.getInstance().processAllKeys(PyVariableNameIndex.KEY, processor, projectAwareSearchScope, idFilter);
	}

	@Override
	public void processElementsWithName(@Nonnull String s, @Nonnull Processor<NavigationItem> processor, @Nonnull FindSymbolParameters findSymbolParameters)
	{
		StubIndex.getInstance().processElements(PyClassNameIndex.KEY, s, findSymbolParameters.getProject(), findSymbolParameters.getSearchScope(), findSymbolParameters.getIdFilter(), PyClass.class,
				processor);
		StubIndex.getInstance().processElements(PyFunctionNameIndex.KEY, s, findSymbolParameters.getProject(), findSymbolParameters.getSearchScope(), findSymbolParameters.getIdFilter(), PyFunction.class,
				processor);
		StubIndex.getInstance().processElements(PyVariableNameIndex.KEY, s, findSymbolParameters.getProject(), findSymbolParameters.getSearchScope(), findSymbolParameters.getIdFilter(), PyTargetExpression.class,
				processor);
	}
}
