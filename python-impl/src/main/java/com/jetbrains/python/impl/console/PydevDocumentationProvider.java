/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.console;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.documentation.AbstractDocumentationProvider;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author oleg
 */
@ExtensionImpl
public class PydevDocumentationProvider extends AbstractDocumentationProvider implements LanguageDocumentationProvider {

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    if (object instanceof PydevConsoleElement){
      return (PydevConsoleElement) object;
    }
    return super.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  @Override
  public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    // Process PydevConsoleElement case
    if (element instanceof PydevConsoleElement){
      return PydevConsoleElement.generateDoc((PydevConsoleElement)element);
    }
    return null;
  }

  @Nullable
  public static String createDoc(final PsiElement element, final PsiElement originalElement) {
    final PyExpression expression = PsiTreeUtil.getParentOfType(originalElement, PyExpression.class);
    // Indicates that we are inside console, not a lookup element!
    if (expression == null){
      return null;
    }
    final ConsoleCommunication communication = PydevConsoleRunner.getConsoleCommunication(originalElement);
    if (communication == null){
      return null;
    }
    try {
      final String description = communication.getDescription(expression.getText());
      return StringUtil.isEmptyOrSpaces(description) ? null : description;
    }
    catch (Exception e) {
      return null;
    }
  }
}
