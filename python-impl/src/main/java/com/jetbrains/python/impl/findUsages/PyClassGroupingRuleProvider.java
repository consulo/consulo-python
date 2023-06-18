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

import javax.annotation.Nonnull;

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

/**
 * @author yole
 */
public class PyClassGroupingRuleProvider implements FileStructureGroupRuleProvider
{
  public UsageGroupingRule getUsageGroupingRule(Project project) {
    return new PyClassGroupingRule();
  }

  private static class PyClassGroupingRule implements UsageGroupingRule {
    public UsageGroup groupUsage(@Nonnull Usage usage) {
      if (!(usage instanceof PsiElementUsage)) return null;
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PyClass pyClass = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
      if (pyClass != null) {
        return new PsiNamedElementUsageGroupBase<PyClass>(pyClass);
      }
      return null;
    }
  }
}
