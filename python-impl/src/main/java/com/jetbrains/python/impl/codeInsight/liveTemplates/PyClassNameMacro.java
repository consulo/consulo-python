// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.liveTemplates;

import com.jetbrains.python.psi.PyClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyClassNameMacro extends Macro {
    @Override
    public String getName() {
        return "pyClassName";
    }

    @Override
    public LocalizeValue getPresentableName() {
        return LocalizeValue.of("pyClassName()");
    }

    @Nullable
    @Override
    @RequiredReadAction
    public Result calculateResult(Expression[] params, ExpressionContext context) {
        PsiElement place = context.getPsiElementAtStartOffset();
        PyClass pyClass = PsiTreeUtil.getParentOfType(place, PyClass.class);
        if (pyClass == null) {
            return null;
        }
        String name = pyClass.getName();
        return name == null ? null : new TextResult(name);
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof PythonTemplateContextType;
    }
}
