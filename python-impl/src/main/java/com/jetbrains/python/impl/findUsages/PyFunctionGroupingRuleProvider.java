// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.usage.PsiNamedElementUsageGroupBase;
import consulo.usage.Usage;
import consulo.usage.UsageGroup;
import consulo.usage.UsageTarget;
import consulo.usage.rule.FileStructureGroupRuleProvider;
import consulo.usage.rule.PsiElementUsage;
import consulo.usage.rule.SingleParentUsageGroupingRule;
import consulo.usage.rule.UsageGroupingRule;
import org.jspecify.annotations.Nullable;

@ExtensionImpl(id = "py-function")
public final class PyFunctionGroupingRuleProvider implements FileStructureGroupRuleProvider {
    @Override
    public UsageGroupingRule getUsageGroupingRule(Project project) {
        return new PyFunctionGroupingRule();
    }

    private static class PyFunctionGroupingRule extends SingleParentUsageGroupingRule {
        @Override
        @RequiredReadAction
        protected @Nullable UsageGroup getParentGroupFor(Usage usage, UsageTarget[] targets) {
            if (!(usage instanceof PsiElementUsage)) {
                return null;
            }
            PsiElement psiElement = ((PsiElementUsage)usage).getElement();
            PyFunction pyFunction = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class, false, PyClass.class);
            if (pyFunction != null) {
                return new PsiNamedElementUsageGroupBase<>(pyFunction);
            }
            return null;
        }
    }
}
