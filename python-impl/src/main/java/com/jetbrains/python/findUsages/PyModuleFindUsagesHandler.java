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

package com.jetbrains.python.findUsages;

import consulo.find.FindUsagesHandler;
import consulo.find.ui.AbstractFindUsagesDialog;
import consulo.find.ui.CommonFindUsagesDialog;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiReference;
import consulo.content.scope.SearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.ui.ex.SimpleTextAttributes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyImportedModule;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyModuleFindUsagesHandler extends FindUsagesHandler
{
  private final PsiFileSystemItem myElement;

  protected PyModuleFindUsagesHandler(@Nonnull PsiFileSystemItem file) {
    super(file);
    final PsiElement e = PyUtil.turnInitIntoDir(file);
    myElement = e instanceof PsiFileSystemItem ? (PsiFileSystemItem)e : file;
  }

  @Nonnull
  @Override
  public PsiElement[] getPrimaryElements() {
    return new PsiElement[] {myElement};
  }

  @Nonnull
  @Override
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    return new CommonFindUsagesDialog(myElement,
                                      getProject(),
                                      getFindUsagesOptions(),
                                      toShowInNewTab,
                                      mustOpenInNewTab,
                                      isSingleFile,
                                      this) {
      @Override
      public void configureLabelComponent(@Nonnull final SimpleColoredComponent coloredComponent) {
        coloredComponent.append(myElement instanceof PsiDirectory ? "Package " : "Module ");
        coloredComponent.append(myElement.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    };
  }

  @Override
  public Collection<PsiReference> findReferencesToHighlight(@Nonnull PsiElement target, @Nonnull SearchScope searchScope) {
    if (target instanceof PyImportedModule) {
      target = ((PyImportedModule) target).resolve();
    }
    if (target instanceof PyFile && PyNames.INIT_DOT_PY.equals(((PyFile)target).getName())) {
      List<PsiReference> result = new ArrayList<PsiReference>();
      result.addAll(super.findReferencesToHighlight(target, searchScope));
      PsiElement targetDir = PyUtil.turnInitIntoDir(target);
      if (targetDir != null) {
        result.addAll(ReferencesSearch.search(targetDir, searchScope, false).findAll());
      }
      return result;
    }
    return super.findReferencesToHighlight(target, searchScope);
  }
}
