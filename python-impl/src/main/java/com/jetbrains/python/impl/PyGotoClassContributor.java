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

package com.jetbrains.python.impl;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.ide.navigation.GotoClassOrTypeContributor;
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
public class PyGotoClassContributor implements GotoClassOrTypeContributor
{
	@Override
	public void processNames(@Nonnull Processor<String> processor, @Nonnull SearchScope searchScope, @Nullable IdFilter idFilter)
	{
		StubIndex.getInstance().processAllKeys(PyClassNameIndex.KEY, processor, (ProjectAwareSearchScope) searchScope, idFilter);
	}

	@Override
	public void processElementsWithName(@Nonnull String s, @Nonnull Processor<NavigationItem> processor, @Nonnull FindSymbolParameters findSymbolParameters)
	{
		StubIndex.getInstance().processElements(PyClassNameIndex.KEY, s, findSymbolParameters.getProject(), findSymbolParameters.getSearchScope(), findSymbolParameters.getIdFilter(), PyClass.class,
				processor);
	}
}
