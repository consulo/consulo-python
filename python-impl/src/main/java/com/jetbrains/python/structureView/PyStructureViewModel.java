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

package com.jetbrains.python.structureView;

import javax.annotation.Nonnull;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * @author yole
 */
public class PyStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider {
  public PyStructureViewModel(@Nonnull PsiFile psiFile) {
    this(psiFile, new PyStructureViewElement((PyElement) psiFile));
    withSorters(Sorter.ALPHA_SORTER);
    withSuitableClasses(PyFunction.class, PyClass.class);
  }

  public PyStructureViewModel(@Nonnull PsiFile file, @Nonnull StructureViewTreeElement element) {
    super(file, element);
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    final Object value = element.getValue();
    return value instanceof PyFile || value instanceof PyClass;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return element.getValue() instanceof PyTargetExpression;
  }

  @Override
  public boolean shouldEnterElement(Object element) {
    return element instanceof PyClass;
  }

  @Nonnull
  @Override
  public Filter[] getFilters() {
    return new Filter[] {
      new PyInheritedMembersFilter(),
      new PyFieldsFilter(),
    };
  }

  @Override
  public boolean isAutoExpand(StructureViewTreeElement element) {
    return element.getValue() instanceof PsiFile;
  }

  @Override
  public boolean isSmartExpand() {
    return false;
  }
}
