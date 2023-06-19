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

package com.jetbrains.python.jython.psi.impl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.jetbrains.python.impl.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.impl.codeInsight.imports.PyImportCandidateProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.jython.module.extension.JythonModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;

/**
 * @author yole
 */
@ExtensionImpl
public class PyJavaImportCandidateProvider implements PyImportCandidateProvider {
  @RequiredReadAction
  @Override
  public void addImportCandidates(PsiReference reference, String name, AutoImportQuickFix quickFix) {
    final PsiElement element = reference.getElement();
    final Project project = element.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(element);

    if (module != null && ModuleUtilCore.getExtension(module, JythonModuleExtension.class) == null) {
      return;
    }

    GlobalSearchScope scope =
      module == null ? (GlobalSearchScope)ProjectScopes.getAllScope(project) : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(
        module,
        false);
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    final PsiClass[] classesByName = cache.getClassesByName(name, scope);
    for (PsiClass psiClass : classesByName) {
      final QualifiedName packageQName = QualifiedName.fromDottedString(psiClass.getQualifiedName()).removeLastComponent();
      quickFix.addImport(psiClass, psiClass.getContainingFile(), packageQName);
    }
  }
}
