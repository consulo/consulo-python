// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PythonDocumentationLinkProvider {
  ExtensionPointName<PythonDocumentationLinkProvider> EP_NAME = ExtensionPointName.create(PythonDocumentationLinkProvider.class);

  @Nullable
  String getExternalDocumentationUrl(PsiElement element, PsiElement originalElement);

  // TODO consulo: org.jsoup library missing - default @Nullable Function<Document, String> quickDocExtractor(PsiNamedElement namedElement) { return null; }
}
