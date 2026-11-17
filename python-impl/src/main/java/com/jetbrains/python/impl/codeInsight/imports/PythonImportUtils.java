// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyStatement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import org.jspecify.annotations.Nullable;

public final class PythonImportUtils {
    private PythonImportUtils() {

    }

    public static @Nullable AutoImportQuickFix proposeImportFix(final PyElement node, PsiReference reference) {
        final String text = reference.getElement().getText();
        final String refText = reference.getRangeInElement().substring(text); // text of the part we're working with

        // don't propose meaningless auto imports if no interpreter is configured
        // Peeks at the snapshot. Each new snapshot restarts the analysis.
        final Module module = ModuleUtilCore.findModuleForPsiElement(node);
        if (module != null) {
            // TODO consulo: EvoPyProjectModelKt.findEvoPyProjectIfReady / EvoPyProject.getInterpreter() missing
            if (PythonSdkType.findPythonSdk(module) == null) {
                return null;
            }
        }

        // don't show auto-import fix if we're trying to reference a variable which is defined below in the same scope
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(node, ScopeOwner.class);
        if (scopeOwner != null && ControlFlowCache.getScope(scopeOwner).containsDeclaration(refText)) {
            return null;
        }

        return addCandidates(node, reference, refText);
    }

    private static @Nullable AutoImportQuickFix addCandidates(PyElement node, PsiReference reference, String refText) {
        return PyImportCollectorFactory.getInstance().create(node, reference, refText).addCandidates();
    }

    public static boolean isImportable(PsiElement refElement) {
        PyStatement parentStatement = PsiTreeUtil.getParentOfType(refElement, PyStatement.class);
        if (parentStatement instanceof PyGlobalStatement || parentStatement instanceof PyNonlocalStatement ||
            parentStatement instanceof PyImportStatementBase) {
            return false;
        }
        return PsiTreeUtil.getParentOfType(refElement, PyPlainStringElement.class, false, PyStatement.class) == null;
    }
}
