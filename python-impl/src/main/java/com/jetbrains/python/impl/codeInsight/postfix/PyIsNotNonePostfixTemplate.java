// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.impl.refactoring.surround.surrounders.expressions.PyIsNotNoneSurrounder;
import consulo.application.dumb.DumbAware;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.refactoring.postfixTemplate.SurroundPostfixTemplateBase;
import consulo.language.editor.surroundWith.Surrounder;

public class PyIsNotNonePostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

    public PyIsNotNonePostfixTemplate(PostfixTemplateProvider provider) {
        super("ifnn", "if expr is not None", PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorTopmost(), provider);
    }

    @Override
    protected Surrounder getSurrounder() {
        return new PyIsNotNoneSurrounder();
    }
}
