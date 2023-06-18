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

package com.jetbrains.python.impl.testing;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiFile;
import consulo.module.Module;

import javax.annotation.Nonnull;

/**
 * Filters out Python unit tests for which it doesn't make sense to run the standard unit test configuration,
 * and which are (possibly) run by other configurations instead.
 *
 * @author yole
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface RunnableUnitTestFilter {
  ExtensionPointName<RunnableUnitTestFilter> EP_NAME = ExtensionPointName.create(RunnableUnitTestFilter.class);

  boolean isRunnableUnitTest(PsiFile script, @Nonnull Module module);
}