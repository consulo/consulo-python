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

package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface QualifiedNameResolver {
  QualifiedNameResolver fromElement(@Nonnull PsiElement foothold);

  QualifiedNameResolver fromModule(@Nonnull Module module);

  QualifiedNameResolver fromSdk(@Nonnull Project project, @Nonnull Sdk sdk);

  QualifiedNameResolver withAllModules();

  QualifiedNameResolver withSdk(Sdk sdk);

  QualifiedNameResolver withRelative(int relativeLevel);

  QualifiedNameResolver withoutRoots();

  QualifiedNameResolver withPlainDirectories();

  @Nonnull
  List<PsiElement> resultsAsList();

  @Nullable
  PsiElement firstResult();

  @Nonnull
  <T extends PsiElement> List<T> resultsOfType(Class<T> clazz);

  @Nullable
  <T extends PsiElement> T firstResultOfType(Class<T> clazz);

  QualifiedNameResolver withContext(QualifiedNameResolveContext context);

  QualifiedNameResolver withoutForeign();

  Module getModule();

  QualifiedNameResolver withMembers();
}
