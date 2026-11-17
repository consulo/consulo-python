// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.liveTemplates;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.macro.Macro;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.TextResult;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.PyNames;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class CollectionElementNameMacro extends Macro {
    @Override
    public String getName() {
        return "collectionElementName";
    }

    @Override
    public LocalizeValue getPresentableName() {
        return LocalizeValue.of("collectionElementName()");
    }

    @Override
    public String getDefaultValue() {
        return "a";
    }

    @Override
    public @Nullable Result calculateResult(Expression[] params, ExpressionContext context) {
        if (params.length != 1) {
            return null;
        }
        Result paramResult = params[0].calculateResult(context);
        if (paramResult == null) {
            return null;
        }
        String param = paramResult.toString();
        int lastDot = param.lastIndexOf('.');
        if (lastDot >= 0) {
            param = param.substring(lastDot + 1);
        }
        if (param.endsWith(")")) {
            int lastParen = param.lastIndexOf('(');
            if (lastParen > 0) {
                param = param.substring(0, lastParen);
            }
        }
        String result = smartUnPluralize(param);
        return result != null && PyNames.isIdentifier(result) ? new TextResult(result) : null;
    }

    private static @Nullable String smartUnPluralize(String param) {
        if (param.endsWith("_list")) {
            return param.substring(0, param.length() - 5);
        }
        return StringUtil.unpluralize(param);
    }

    @Override
    public LookupElement @Nullable [] calculateLookupItems(Expression[] params, ExpressionContext context) {
        Result result = calculateResult(params, context);
        if (result == null) {
            return null;
        }
        String[] words = result.toString().split("_");
        if (words.length > 1) {
            List<LookupElement> lookup = new ArrayList<>();
            for (int i = 0; i < words.length; i++) {
                String element = StringUtil.join(words, i, words.length, "_");
                lookup.add(LookupElementBuilder.create(element));
            }
            return lookup.toArray(LookupElement.EMPTY_ARRAY);
        }
        return null;
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof PythonTemplateContextType;
    }
}
