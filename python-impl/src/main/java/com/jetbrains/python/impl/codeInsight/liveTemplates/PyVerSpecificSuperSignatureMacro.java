// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.liveTemplates;

import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.macro.Macro;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.TextResult;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyVerSpecificSuperSignatureMacro extends Macro {
    @Override
    public String getName() {
        return "pyVerSpecificSuperSignature";
    }

    @Override
    public LocalizeValue getPresentableName() {
        return LocalizeValue.of("pyVerSpecificSuperSignature()");
    }

    @Override
    public @Nullable Result calculateResult(Expression[] params, ExpressionContext context) {
        if (context.getPsiElementAtStartOffset() != null) {
            if (LanguageLevel.forElement(context.getPsiElementAtStartOffset()).isPython2()) {
                Result classNameResult = new PyClassNameMacro().calculateResult(new Expression[]{}, context);
                if (classNameResult != null) {
                    String className = classNameResult.toString();
                    String self = "self";
                    return new TextResult(className + ", " + self);
                }
            }
        }
        return new TextResult("");
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof PythonTemplateContextType;
    }
}
