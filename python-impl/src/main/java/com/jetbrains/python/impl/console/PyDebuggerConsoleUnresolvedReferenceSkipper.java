// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiReference;
import consulo.virtualFileSystem.VirtualFile;

import static com.jetbrains.python.impl.console.PythonConsoleView.CONSOLE_KEY;

/**
 * If static code insight is enabled then ignore unresolved reference from Debugger Console
 */
@ExtensionImpl
public final class PyDebuggerConsoleUnresolvedReferenceSkipper extends PyInspectionExtension {
    @Override
    @RequiredReadAction
    public boolean ignoreUnresolvedReference(PyElement node, PsiReference reference, TypeEvalContext context) {
        VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
        return virtualFile != null && Boolean.TRUE.equals(virtualFile.getUserData(CONSOLE_KEY)) &&
            !(context.getOrigin() instanceof PyExpressionCodeFragment);
    }
}
