// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.console.PyConsoleUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;

/**
 * Suppresses code completion in the input editor of a Python console, in particular while the console passes
 * the typed text to the stdin of the running program.
 *
 * @see PyConsoleUtil#isCodeCompletionSuppressed(consulo.language.psi.PsiFile)
 */
// TODO consulo: JB registers this contributor with order="first"
@ExtensionImpl(id = "pythonConsoleCompletionBlocking")
public final class PythonConsoleCompletionBlockingContributor extends CompletionContributor implements DumbAware {
    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        if (PyConsoleUtil.isCodeCompletionSuppressed(parameters.getOriginalFile())) {
            result.stopHere();
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
