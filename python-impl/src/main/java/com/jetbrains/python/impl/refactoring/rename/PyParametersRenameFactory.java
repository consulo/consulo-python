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
import consulo.language.editor.refactoring.rename.AutomaticRenamer;
import consulo.util.lang.Comparing;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.rename.AutomaticRenamerFactory;
import consulo.usage.UsageInfo;
import consulo.application.util.function.Processor;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;

import java.util.Collection;

/**
 * @author yole
 */
@ExtensionImpl
public class PyParametersRenameFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(PsiElement element) {
    if (element instanceof PyParameter) {
      PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      return function != null && function.getContainingClass() != null;
    }
    return false;
  }

  @Override
  public String getOptionName() {
    return "Rename parameters in hierarchy";
  }

  @Override
  public boolean isEnabled() {
    return PyCodeInsightSettings.getInstance().RENAME_PARAMETERS_IN_HIERARCHY;
  }

  @Override
  public void setEnabled(boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_PARAMETERS_IN_HIERARCHY = enabled;
  }

  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new PyParametersRenamer((PyParameter)element, newName);
  }

  public static class PyParametersRenamer extends AutomaticRenamer
  {

    public PyParametersRenamer(final PyParameter element, String newName) {
      PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      PyOverridingMethodsSearch.search(function, true).forEach(new Processor<PyFunction>() {
        @Override
        public boolean process(PyFunction pyFunction) {
          PyParameter[] parameters = pyFunction.getParameterList().getParameters();
          for (PyParameter parameter : parameters) {
            PyNamedParameter named = parameter.getAsNamed();
            if (named != null && Comparing.equal(named.getName(), element.getName())) {
              myElements.add(named);
            }
          }
          return true;
        }
      });
      suggestAllNames(element.getName(), newName);
    }

    @Override
    public String getDialogTitle() {
      return "Rename parameters";
    }

    @Override
    public String getDialogDescription() {
      return "Rename parameter in hierarchy to:";
    }

    @Override
    public String entityName() {
      return "Parameter";
    }

    @Override
    public boolean isSelectedByDefault() {
      return true;
    }
  }
}
