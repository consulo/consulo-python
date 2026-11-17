// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyStatement;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.navigation.GotoDeclarationHandlerBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Provides reaction on ctrl+click for {@code break} and {@code continue} statements.
 */
@ExtensionImpl
public final class PyBreakContinueGotoProvider extends GotoDeclarationHandlerBase {
    @Override
    @RequiredReadAction
    public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement source, Editor editor) {
        if (source != null && source.getLanguage() instanceof PythonLanguage) {
            PsiElement breakOrContinue = source.getParent();
            PyLoopStatement loop = PyUtil.getCorrespondingLoop(breakOrContinue);
            if (loop != null) {
                if (breakOrContinue instanceof PyContinueStatement) {
                    return loop;
                }
                else {
                    PsiElement outer_element = loop;
                    PsiElement after_cycle;
                    while (true) {
                        after_cycle = outer_element.getNextSibling();
                        if (after_cycle != null) {
                            if (after_cycle instanceof PsiWhiteSpace) {
                                after_cycle = after_cycle.getNextSibling();
                            }
                            if (after_cycle instanceof PyStatement) {
                                return after_cycle;
                            }
                        }
                        outer_element = outer_element.getParent();
                        if (PsiTreeUtil.instanceOf(outer_element, PsiFile.class, PyFunction.class, PyClass.class)) {
                            break;
                        }
                    }
                    // cycle is the last statement in the flow of execution. its last element is our best bet.
                    return PsiTreeUtil.getDeepestLast(loop);
                }
            }
        }

        return null;
    }
}
