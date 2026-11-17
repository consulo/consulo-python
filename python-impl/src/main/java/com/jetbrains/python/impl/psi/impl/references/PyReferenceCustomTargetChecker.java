// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;

/**
 * EP to check if some reference points to some element
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyReferenceCustomTargetChecker {
    final class Companion {
        private static final ExtensionPointName<PyReferenceCustomTargetChecker> EP_NAME =
            ExtensionPointName.create(PyReferenceCustomTargetChecker.class);

        private Companion() {
        }

        public static boolean isReferenceTo(PsiReference reference, PsiElement to) {
            for (PyReferenceCustomTargetChecker it : EP_NAME.getExtensionList()) {
                if (it.isReferenceTo(reference, to)) {
                    return true;
                }
            }
            return false;
        }
    }

    boolean isReferenceTo(PsiReference reference, PsiElement to);
}
