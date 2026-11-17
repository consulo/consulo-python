// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.GoogleCodeStyleDocString;
import com.jetbrains.python.documentation.docstrings.NumpyDocString;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.StringUtil;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class DocStringSectionHeaderCompletionContributor extends CompletionContributor implements DumbAware {
    public DocStringSectionHeaderCompletionContributor() {
        extend(CompletionType.BASIC, psiElement().withParent(DocStringTagCompletionContributor.Helper.DOCSTRING_PATTERN),
            new CompletionProvider() {
                @Override
                public void addCompletions(CompletionParameters parameters,
                                           ProcessingContext context,
                                           CompletionResultSet result) {
                    final PsiFile file = parameters.getOriginalFile();
                    final PsiElement stringNode = parameters.getOriginalPosition();
                    assert stringNode != null;
                    final int offset = parameters.getOffset();
                    final DocStringFormat format = DocStringParser.getConfiguredDocStringFormat(file);
                    if (!(format == DocStringFormat.GOOGLE || format == DocStringFormat.NUMPY)) {
                        return;
                    }
                    // Numpy docstring format is ambiguous. Because parameters have the same indentation as section headers,
                    // beginning of section header can be parsed as parameter reference
                    if (format == DocStringFormat.GOOGLE && file.findReferenceAt(offset) != null) {
                        return;
                    }
                    final Document document = parameters.getEditor().getDocument();
                    final TextRange linePrefixRange = new TextRange(document.getLineStartOffset(document.getLineNumber(offset)), offset);
                    final String prefix = StringUtil.trimLeading(document.getText(linePrefixRange));
                    result = result.withPrefixMatcher(prefix).caseInsensitive();
                    final Iterable<String> names = format == DocStringFormat.GOOGLE ? GoogleCodeStyleDocString.PREFERRED_SECTION_HEADERS
                        : NumpyDocString.PREFERRED_SECTION_HEADERS;
                    for (String tag : names) {
                        result.addElement(LookupElementBuilder.create(tag));
                    }
                }
            });
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
