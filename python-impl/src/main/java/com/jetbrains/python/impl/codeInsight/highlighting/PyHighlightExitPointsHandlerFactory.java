// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.highlighting;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerFactoryBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author oleg
 */
@ExtensionImpl
public final class PyHighlightExitPointsHandlerFactory extends HighlightUsagesHandlerFactoryBase implements DumbAware {
    @Override
    public @Nullable HighlightUsagesHandlerBase createHighlightUsagesHandler(Editor editor, PsiFile psiFile, PsiElement target) {
        PyReturnStatement returnStatement = PsiTreeUtil.getParentOfType(target, PyReturnStatement.class);
        if (returnStatement != null) {
            PyExpression returnExpr = returnStatement.getExpression();
            if (returnExpr == null || !PsiTreeUtil.isAncestor(returnExpr, target, false)) {
                return new PyHighlightExitPointsHandler(editor, psiFile, target);
            }
        }
        return null;
    }
}
