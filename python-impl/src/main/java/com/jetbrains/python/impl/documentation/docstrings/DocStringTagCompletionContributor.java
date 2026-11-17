// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.SphinxDocString;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;

import static com.jetbrains.python.impl.documentation.docstrings.DocStringTagCompletionContributor.Helper.DOCSTRING_PATTERN;

@ExtensionImpl
public class DocStringTagCompletionContributor extends CompletionContributor implements DumbAware {
    public DocStringTagCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(DOCSTRING_PATTERN),
            new CompletionProvider() {
                @Override
                public void addCompletions(
                    CompletionParameters parameters,
                    ProcessingContext context,
                    CompletionResultSet result
                ) {
                    PsiFile file = parameters.getOriginalFile();
                    if (DocStringParser.getConfiguredDocStringFormat(file) == DocStringFormat.REST) {
                        int offset = parameters.getOffset();
                        String text = file.getText();
                        char prefix = ':';
                        if (offset > 0) {
                            offset--;
                        }
                        StringBuilder prefixBuilder = new StringBuilder();
                        while (offset > 0 && (Character.isLetterOrDigit(text.charAt(offset)) || text.charAt(offset) == prefix)) {
                            prefixBuilder.insert(0, text.charAt(offset));
                            if (text.charAt(offset) == prefix) {
                                offset--;
                                break;
                            }
                            offset--;
                        }
                        while (offset > 0) {
                            offset--;
                            if (text.charAt(offset) == '\n' || text.charAt(offset) == '\"' || text.charAt(offset) == '\'') {
                                break;
                            }
                            if (!Character.isWhitespace(text.charAt(offset))) {
                                return;
                            }
                        }
                        CompletionResultSet resultSet = result;
                        if (!prefixBuilder.isEmpty()) {
                            resultSet = resultSet.withPrefixMatcher(prefixBuilder.toString());
                        }
                        for (String tag : SphinxDocString.ALL_TAGS) {
                            resultSet.addElement(LookupElementBuilder.create(tag));
                        }
                    }
                }
            });
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    public static final class Helper {
        public static final PsiElementPattern.Capture<PyStringLiteralExpression> DOCSTRING_PATTERN =
            PlatformPatterns.psiElement(PyStringLiteralExpression.class)
                .withParent(
                    PlatformPatterns
                        .psiElement(PyExpressionStatement.class)
                        .inside(PyDocStringOwner.class));

        private Helper() {
        }
    }
}
