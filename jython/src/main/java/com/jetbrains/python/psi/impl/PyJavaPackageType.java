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
package com.jetbrains.python.psi.impl;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ProcessingContext;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.ArrayUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyJavaPackageType implements PyType {
  private final PsiJavaPackage myPackage;
  @Nullable
  private final Module myModule;

  public PyJavaPackageType(PsiJavaPackage aPackage, @Nullable Module module) {
    myPackage = aPackage;
    myModule = module;
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@Nonnull String name,
                                                          @Nullable PyExpression location,
                                                          @Nonnull AccessDirection direction,
                                                          @Nonnull PyResolveContext resolveContext) {
    Project project = myPackage.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    String childName = myPackage.getQualifiedName() + "." + name;
    GlobalSearchScope scope = getScope(project);
    ResolveResultList result = new ResolveResultList();
    final PsiClass[] classes = facade.findClasses(childName, scope);
    for (PsiClass aClass : classes) {
      result.poke(aClass, RatedResolveResult.RATE_NORMAL);
    }
    final PsiJavaPackage psiPackage = facade.findPackage(childName);
    if (psiPackage != null) {
      result.poke(psiPackage, RatedResolveResult.RATE_NORMAL);
    }
    return result;
  }

  private GlobalSearchScope getScope(Project project) {
    return myModule != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule,
                                                                                        false) : (GlobalSearchScope)ProjectScopes.getAllScope(
      project);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    List<Object> variants = new ArrayList<>();
    final GlobalSearchScope scope = getScope(location.getProject());
    final PsiClass[] classes = myPackage.getClasses(scope);
    for (PsiClass psiClass : classes) {
      variants.add(LookupElementBuilder.create(psiClass).withIcon(IconDescriptorUpdaters.getIcon(psiClass, 0)));
    }
    final PsiJavaPackage[] subPackages = myPackage.getSubPackages(scope);
    for (PsiJavaPackage subPackage : subPackages) {
      variants.add(LookupElementBuilder.create(subPackage).withIcon(IconDescriptorUpdaters.getIcon(subPackage, 0)));
    }
    return ArrayUtil.toObjectArray(variants);
  }

  @Override
  public String getName() {
    return myPackage.getQualifiedName();
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }
}
