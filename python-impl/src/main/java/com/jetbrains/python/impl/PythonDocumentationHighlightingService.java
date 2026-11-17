// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.colorScheme.TextAttributesKey;
import consulo.project.Project;

@ServiceAPI(ComponentScope.APPLICATION)
public class PythonDocumentationHighlightingService {
    public static PythonDocumentationHighlightingService getInstance() {
        return Application.get().getInstance(PythonDocumentationHighlightingService.class);
    }

    public String highlightedCodeSnippet(Project project, String codeSnippet) {
        return codeSnippet;
    }

    public String styledSpan(TextAttributesKey textAttributeKey, String text) {
        return text;
    }

    public String highlightCodeBlockInHtml(Project project, String codeBlock) {
        return codeBlock;
    }
}
