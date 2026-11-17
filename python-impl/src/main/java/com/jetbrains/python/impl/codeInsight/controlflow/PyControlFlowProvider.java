// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowProvider;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PyControlFlowProvider implements ControlFlowProvider {
    @Override
    public @Nullable ControlFlow getControlFlow(PsiElement element) {
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(element);
        if (scopeOwner == null) {
            return null;
        }
        return ControlFlowCache.getControlFlow(scopeOwner);
    }

    @Override
    public @Nullable String getAdditionalInfo(Instruction instruction) {
        if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
            return readWriteInstruction.getAccess() + " " + readWriteInstruction.getName();
        }
        if (instruction instanceof RefutablePatternInstruction refutablePatternInstruction) {
            return refutablePatternInstruction.getElementPresentation();
        }
        return null;
    }
}
