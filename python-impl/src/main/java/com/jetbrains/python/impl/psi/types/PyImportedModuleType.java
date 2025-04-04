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
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.Sets;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyImportedModuleType implements PyType {
  @Nonnull
  private PyImportedModule myImportedModule;

  public PyImportedModuleType(@Nonnull PyImportedModule importedModule) {
    myImportedModule = importedModule;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@Nonnull String name,
                                                          @Nullable PyExpression location,
                                                          @Nonnull AccessDirection direction,
                                                          @Nonnull PyResolveContext resolveContext) {
    final PsiElement resolved = myImportedModule.resolve();
    if (resolved != null) {
      final PsiFile containingFile = location != null ? location.getContainingFile() : null;
      List<PsiElement> elements = Collections.singletonList(ResolveImportUtil.resolveChild(resolved, name, containingFile, false, true));
      final PyImportElement importElement = myImportedModule.getImportElement();
      final PyFile resolvedFile = PyUtil.as(resolved, PyFile.class);
      if (location != null && importElement != null && PyUtil.inSameFile(location, importElement) &&
        ResolveImportUtil.getPointInImport(location) == PointInImport.NONE && resolved instanceof PsiFileSystemItem &&
        (resolvedFile == null || !PyUtil.isPackage(resolvedFile) || resolvedFile.getElementNamed(name) == null)) {
        final List<PsiElement> importedSubmodules = PyModuleType.collectImportedSubmodules((PsiFileSystemItem)resolved, location);
        if (importedSubmodules != null) {
          final Set<PsiElement> imported = Sets.newHashSet(importedSubmodules);
          elements = ContainerUtil.filter(elements, element -> imported.contains(element));
        }
      }
      return ResolveImportUtil.rateResults(elements);
    }
    return null;
  }

  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<LookupElement> result = new ArrayList<>();
    final PsiElement resolved = myImportedModule.resolve();
    if (resolved instanceof PyFile) {
      final PyModuleType moduleType = new PyModuleType((PyFile)resolved, myImportedModule);
      result.addAll(moduleType.getCompletionVariantsAsLookupElements(location, context, false, false));
    }
    else if (resolved instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)resolved;
      if (PyUtil.isPackage(dir, location)) {
        if (ResolveImportUtil.getPointInImport(location) != PointInImport.NONE) {
          result.addAll(PyModuleType.getSubModuleVariants(dir, location, null));
        }
        else {
          result.addAll(PyModuleType.collectImportedSubmodulesAsLookupElements(dir, location, context.get(CTX_NAMES)));
        }
      }
    }
    return ArrayUtil.toObjectArray(result);
  }

  public String getName() {
    return "imported module " + myImportedModule.getImportedPrefix().toString();
  }

  @Override
  public boolean isBuiltin() {
    return false;  // no module can be imported from builtins
  }

  @Override
  public void assertValid(String message) {
  }

  @Nonnull
  public PyImportedModule getImportedModule() {
    return myImportedModule;
  }
}
