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

package com.jetbrains.python.impl.findUsages;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.PsiNamedElementUsageGroupBase;
import consulo.usage.Usage;
import consulo.usage.UsageGroup;
import consulo.usage.rule.FileStructureGroupRuleProvider;
import consulo.usage.rule.PsiElementUsage;
import consulo.usage.rule.UsageGroupingRule;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl(id = "py-function")
public class PyFunctionGroupingRuleProvider implements FileStructureGroupRuleProvider {
  public UsageGroupingRule getUsageGroupingRule(Project project) {
    return new PyFunctionGroupingRule();
  }

  private static class PyFunctionGroupingRule implements UsageGroupingRule
  {
    public UsageGroup groupUsage(@Nonnull Usage usage) {
      if (!(usage instanceof PsiElementUsage)) return null;
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PyFunction pyFunction = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class, false, PyClass.class);
      if (pyFunction != null) {
        return new PsiNamedElementUsageGroupBase<PyFunction>(pyFunction);
      }
      return null;
    }
  }
}