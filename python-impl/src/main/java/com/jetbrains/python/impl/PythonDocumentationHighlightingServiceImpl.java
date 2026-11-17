// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ServiceImpl;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.editor.codeBlock.CodeBlockHighlighter;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@ServiceImpl
@Singleton
public class PythonDocumentationHighlightingServiceImpl extends PythonDocumentationHighlightingService {
    private static final Logger LOG = Logger.getInstance(PythonDocumentationHighlightingServiceImpl.class);

    private final CodeBlockHighlighter myCodeBlockHighlighter;

    @Inject
    public PythonDocumentationHighlightingServiceImpl(CodeBlockHighlighter codeBlockHighlighter) {
        myCodeBlockHighlighter = codeBlockHighlighter;
    }

    @Override
    public String highlightedCodeSnippet(Project project, String codeSnippet) {
        // TODO consulo: QuickDocHighlightingHelper.getStyledCodeFragment missing - CodeBlockHighlighter is used instead,
        //  DocumentationSettings.getHighlightingSaturation missing - default saturation factor is used
        return myCodeBlockHighlighter.newHtmlBuilder(project, PythonLanguage.INSTANCE, codeSnippet).build();
    }

    @Override
    public String styledSpan(TextAttributesKey textAttributeKey, String text) {
        // TODO consulo: QuickDocHighlightingHelper.getStyledFragment missing - CodeBlockHighlighter is used instead,
        //  DocumentationSettings.getHighlightingSaturation missing - default saturation factor is used
        return myCodeBlockHighlighter.appendStyledSpan(new StringBuilder(), textAttributeKey, text, 1f).toString();
    }

    @Override
    public String highlightCodeBlockInHtml(Project project, String codeBlock) {
        if (codeBlock.isBlank()) {
            return codeBlock;
        }

        // TODO consulo: org.jsoup missing - JB logic:
        //  try {
        //      Document document = Jsoup.parse(codeBlock);
        //      Elements preElements = document.select("pre[data-language]");
        //      for (Element preElement : preElements) {
        //          Element codeElement = preElement.selectFirst("code");
        //          if (codeElement == null) continue;
        //          String codeText = codeElement.wholeText();
        //          if (codeText.isBlank()) continue;
        //          String languageName = preElement.attr("data-language");
        //          Language language = guessLanguage(languageName);
        //          if (language == null) continue;
        //          String highlightedHtml = ReadAction.compute(() -> getStyledCodeFragment(project, language, codeText));
        //          codeElement.html(highlightedHtml);
        //      }
        //      return document.body().html();
        //  }
        //  catch (Throwable e) {
        //      LOG.debug("Failed to apply syntax highlighting to code block in rendered documentation", e);
        //      return codeBlock;
        //  }
        return codeBlock;
    }
}
