// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inlayHints;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.impl.PyClassPatternImpl;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.inlay.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

import java.util.List;

/**
 * Provides inlays for positional patterns in class patterns, showing pseudo keyword argument names.
 */
@ExtensionImpl
public class PyPseudoKeywordPatternInlayHintsProvider implements DeclarativeInlayHintsProvider {
    @Override
    public DeclarativeInlayHintsCollector createCollector(PsiFile file, Editor editor) {
        return new Collector();
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    @Override
    public String getId() {
        return "python.pseudo.keyword.pattern.inlays";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.inlayPositionalArgsPatterns();
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.empty();
    }

    @Override
    public LocalizeValue getPreviewFileText() {
        // resources/inlayProviders/python.pseudo.keyword.pattern.inlays/preview.py
        return LocalizeValue.of("""
            class Point:
                __match_args__ = ("x", "y")

            def f(p: Point):
                match p:
                    case Point(/*<# x= #>*/1, /*<# y= #>*/2):
                        pass""");
    }

    @Override
    public InlayGroup getGroup() {
        return InlayGroup.TYPES_GROUP;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    private static class Collector implements DeclarativeInlayHintsCollector.SharedBypassCollector {
        private final HintFormat hintFormat = HintFormat.DEFAULT
            .withFontSize(HintFontSize.ABitSmallerThanInEditor)
            .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding);

        @Override
        public void collectFromElement(PsiElement element, DeclarativeInlayTreeSink sink) {
            TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
            if (!(element instanceof PyClassPattern classPattern)) {
                return;
            }
            PyType type = typeEvalContext.getType(classPattern);
            if (!(type instanceof PyClassType classType)) {
                return;
            }
            List<String> matchArgs = PyClassPatternImpl.getMatchArgs(classType, typeEvalContext);
            if (matchArgs == null) {
                return;
            }
            List<PyPattern> patterns = classPattern.getArgumentList().getPatterns();
            for (int idx = 0; idx < patterns.size(); idx++) {
                PyPattern pattern = patterns.get(idx);
                if (pattern instanceof PyKeywordPattern) {
                    return; // positional patterns can only be before keyword patterns
                }
                String name = idx < matchArgs.size() ? StringUtil.nullize(matchArgs.get(idx)) : null;
                if (name == null) {
                    continue;
                }
                sink.addPresentation(
                    new DeclarativeInlayPosition.InlineInlayPosition(pattern.getTextRange().getStartOffset(), false),
                    hintFormat,
                    builder -> {
                        InlayActionData navigationData = new InlayActionData(
                            PyPseudoKeywordPatternInlayActionHandler.payload(SmartPointerManager.createPointer(classPattern), name),
                            PyPseudoKeywordPatternInlayActionHandler.HANDLER_NAME
                        );
                        builder.text(name, navigationData);
                        builder.text("=");
                    }
                );
            }
        }
    }
}
