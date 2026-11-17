// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.impl.console.PydevConsoleRunner;
import com.jetbrains.python.impl.console.PydevDocumentationProvider;
import com.jetbrains.python.impl.console.completion.PydevConsoleReference;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.annotation.component.ServiceImpl;
import consulo.content.bundle.Sdk;
import consulo.language.ast.ASTNode;
import consulo.language.editor.scratch.ScratchUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiUtilCore;
import consulo.module.Module;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ServiceImpl
@Singleton
public final class PythonRuntimeServiceImpl extends PythonRuntimeService {
  @Override
  public boolean isInPydevConsole(PsiElement file) {
    // consulo: JB PydevConsoleRunnerUtil.isInPydevConsole lives in PydevConsoleRunner
    return PydevConsoleRunner.isInPydevConsole(file);
  }

  @Override
  public boolean isInScratchFile(PsiElement element) {
    return ScratchUtil.isScratch(PsiUtilCore.getVirtualFile(element));
  }

  @Override
  public boolean isExternallyIndexedFile(PsiElement element) {
    // TODO consulo: com.intellij.python.externalIndex.PyExternalFilesIndexService missing
    return false;
  }

  @Override
  public @Nullable Sdk getConsoleSdk(PsiElement foothold) {
    return PydevConsoleRunner.getConsoleSdk(foothold);
  }

  @Override
  public @Nullable String createPydevDoc(PsiElement element, PsiElement originalElement) {
    return PydevDocumentationProvider.createDoc(element, originalElement);
  }

  @Override
  public LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  @Override
  public @Nullable PsiPolyVariantReference getPydevConsoleReference(PyReferenceExpression element,
                                                                    PyResolveContext context) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      final ConsoleCommunication communication = file.getCopyableUserData(PydevConsoleRunner.CONSOLE_KEY);
      // TODO consulo: PyConsoleOptions.isRuntimeCodeCompletion missing
      if (communication != null) {
        PyExpression qualifier = element.getQualifier();
        final String prefix = qualifier == null ? "" : qualifier.getText() + ".";
        return new PydevConsoleReference(element, communication, prefix, context.allowRemote());
      }
      // TODO consulo: PythonDebugConsoleCommunication.setContext missing
    }
    return null;
  }

  @Override
  public @Nullable PythonConsoleData getPythonConsoleData(@Nullable ASTNode node) {
    return PydevConsoleRunner.getPythonConsoleData(node);
  }

  @Override
  public @Nullable String formatDocstring(Module module,
                                         DocStringFormat format,
                                         String input,
                                         List<String> formatterFlags) {
    // TODO consulo: com.jetbrains.python.documentation.PyRuntimeDocstringFormatter missing
    return null;
  }

  @Override
  public String mapToRemote(String localRoot, Sdk sdk) {
    // TODO consulo: com.intellij.remote.RemoteSdkAdditionalData path mappings missing
    return localRoot;
  }
}
