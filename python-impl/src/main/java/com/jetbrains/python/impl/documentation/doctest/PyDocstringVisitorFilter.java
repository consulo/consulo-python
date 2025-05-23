/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.inspections.*;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.validation.DocStringAnnotator;
import com.jetbrains.python.impl.validation.HighlightingAnnotator;
import com.jetbrains.python.impl.validation.ParameterListAnnotator;
import com.jetbrains.python.impl.validation.ReturnAnnotator;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nonnull;

/**
 * User : ktisha
 * <p>
 * filter out some python inspections and annotations if we're in docstring substitution
 */
@ExtensionImpl
public class PyDocstringVisitorFilter implements PythonVisitorFilter
{
  @Override
  public boolean isSupported(@Nonnull final Class visitorClass, @Nonnull final PsiFile file) {
    //inspections
    if (visitorClass == PyArgumentListInspection.class) {
      return false;
    }
    if (visitorClass == PyIncorrectDocstringInspection.class || visitorClass == PyMissingOrEmptyDocstringInspection.class ||
      visitorClass == PyUnboundLocalVariableInspection.class || visitorClass == PyUnnecessaryBackslashInspection.class ||
      visitorClass == PyByteLiteralInspection.class || visitorClass == PyNonAsciiCharInspection.class ||
      visitorClass == PyPackageRequirementsInspection.class || visitorClass == PyMandatoryEncodingInspection.class ||
      visitorClass == PyInterpreterInspection.class || visitorClass == PyDocstringTypesInspection.class ||
      visitorClass == PySingleQuotedDocstringInspection.class || visitorClass == PyClassHasNoInitInspection.class ||
      visitorClass == PyStatementEffectInspection.class || visitorClass == PyPep8Inspection.class) {
      return false;
    }
    //annotators
    if (visitorClass == DocStringAnnotator.class || visitorClass == ParameterListAnnotator.class || visitorClass == ReturnAnnotator.class || visitorClass == HighlightingAnnotator.class) {
      return false;
    }
    // doctest in separate file
    final PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    if (visitorClass == PyUnresolvedReferencesInspection.class && !(topLevelFile instanceof PyFile)) {
      return false;
    }
    return true;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PyDocstringLanguageDialect.INSTANCE;
  }
}
