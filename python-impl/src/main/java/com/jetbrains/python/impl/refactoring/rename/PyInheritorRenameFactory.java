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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.rename.AutomaticRenamerFactory;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageInfo;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.psi.search.PyClassInheritorsSearch;

import java.util.Collection;

/**
 * @author yole
 */
@ExtensionImpl
public class PyInheritorRenameFactory implements AutomaticRenamerFactory
{
  @Override
  public boolean isApplicable(PsiElement element) {
    return element instanceof PyClass;
  }

  @Override
  public LocalizeValue getOptionName() {
    return LocalizeValue.localizeTODO("Rename inheritors");
  }

  @Override
  public boolean isEnabled() {
    return PyCodeInsightSettings.getInstance().RENAME_CLASS_INHERITORS;
  }

  @Override
  public void setEnabled(boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_CLASS_INHERITORS = enabled;
  }

  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new PyInheritorRenamer((PyClass) element, newName);
  }

  public static class PyInheritorRenamer extends AutomaticRenamer {
    public PyInheritorRenamer(PyClass element, String newName) {
      myElements.addAll(PyClassInheritorsSearch.search(element, false).findAll());
      suggestAllNames(element.getName(), newName);
    }

    @Override
    public String getDialogTitle() {
      return "Rename Inheritors";
    }

    @Override
    public String getDialogDescription() {
      return "Rename inheritor classes with the following names to:";
    }

    @Override
    public String entityName() {
      return "Inheritor Class";
    }
  }
}
