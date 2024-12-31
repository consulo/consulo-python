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

package com.jetbrains.python.impl.refactoring.rename;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import consulo.util.io.FileUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@ExtensionImpl(id = "pyFile")
public class RenamePyFileProcessor extends consulo.ide.impl.idea.refactoring.rename.RenamePsiFileProcessor {
  @Override
  public boolean canProcessElement(@Nonnull PsiElement element) {
    return element instanceof PyFile;
  }

  @Override
  public PsiElement substituteElementToRename(PsiElement element, @Nullable Editor editor) {
    PyFile file = (PyFile) element;
    if (file.getName().equals(PyNames.INIT_DOT_PY)) {
      return file.getParent();
    }
    return element;
  }

  @Nonnull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    final List<PsiReference> results = new ArrayList<PsiReference>();
    for (PsiReference reference : super.findReferences(element)) {
      if (isNotAliasedInImportElement(reference)) {
        results.add(reference);
      }
    }
    return results;
  }

  @Override
  public void findCollisions(PsiElement element,
                             final String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    final String newFileName = FileUtil.getNameWithoutExtension(newName);
    if (!PyNames.isIdentifier(newFileName)) {
      List<UsageInfo> usages = new ArrayList<UsageInfo>(result);
      for (UsageInfo usageInfo : usages) {
        final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(usageInfo.getElement(), PyImportStatementBase.class);
        if (importStatement != null) {
          result.add(new UnresolvableCollisionUsageInfo(importStatement, element) {
            @Override
            public String getDescription() {
              return "The name '" + newFileName + "' is not a valid Python identifier. Cannot update import statement in '" +
                     importStatement.getContainingFile().getName() + "'";
            }
          });
        }
      }
    }
  }

  private static boolean isNotAliasedInImportElement(@Nonnull PsiReference reference) {
    boolean include = true;
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      for (ResolveResult result : results) {
        final PsiElement resolved = result.getElement();
        if (resolved instanceof PyImportElement) {
          if (((PyImportElement)resolved).getAsName() != null) {
            include = false;
            break;
          }
        }
      }
    }
    return include;
  }
}
