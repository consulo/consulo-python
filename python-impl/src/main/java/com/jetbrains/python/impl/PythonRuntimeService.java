// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.module.Module;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ServiceAPI(ComponentScope.APPLICATION)
public class PythonRuntimeService {

  public boolean isInPydevConsole(PsiElement file) {
    return false;
  }

  public @Nullable Sdk getConsoleSdk(PsiElement foothold) {
    return null;
  }

  public @Nullable String createPydevDoc(PsiElement element, PsiElement originalElement) {
    return null;
  }

  public LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    return LanguageLevel.getDefault();
  }

  public @Nullable PsiPolyVariantReference getPydevConsoleReference(PyReferenceExpression element,
                                                                    PyResolveContext context) {
    return null;
  }

  public @Nullable PythonConsoleData getPythonConsoleData(@Nullable ASTNode node) {
    return null;
  }

  public @Nullable String formatDocstring(Module module,
                                          DocStringFormat format,
                                          String input,
                                          List<String> formatterFlags) {
    return null;
  }

  public String mapToRemote(String localRoot, Sdk sdk) {
    return localRoot;
  }

  public boolean isInScratchFile(PsiElement element) {
    return false;
  }

  public boolean isExternallyIndexedFile(PsiElement element) {
    return false;
  }

  public static PythonRuntimeService getInstance() {
    return Application.get().getInstance(PythonRuntimeService.class);
  }
}
