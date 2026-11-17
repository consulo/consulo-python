// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.magicLiteral;

import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.ide.impl.find.PsiElement2UsageTargetAdapter;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageTarget;
import consulo.usage.UsageTargetProvider;
import org.jspecify.annotations.Nullable;

/**
 * Supports usage info for magic literals.
 * <p/>
 * <strong>Install it</strong> as "usageTargetProvider" !
 *
 * @author Ilya.Kazakevich
 */
@ExtensionImpl
public final class PyMagicLiteralUsageTargetProvider implements UsageTargetProvider, DumbAware {
    @Override
    public UsageTarget @Nullable [] getTargets(Editor editor, PsiFile file) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        if (element != null) {
            PyStringLiteralExpression literal = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
            if ((literal != null)) {
                return new UsageTarget[]{new PsiElement2UsageTargetAdapter(literal)};
            }
        }
        return UsageTarget.EMPTY_ARRAY;
    }

    @Override
    public UsageTarget @Nullable [] getTargets(PsiElement psiElement) {
        // Consulo: UsageTargetProvider requires the element-based variant, JB provides targets only for the editor caret
        return null;
    }
}
