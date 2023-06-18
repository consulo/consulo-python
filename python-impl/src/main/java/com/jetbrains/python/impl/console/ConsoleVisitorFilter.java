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
package com.jetbrains.python.impl.console;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.*;
import com.jetbrains.python.impl.validation.DocStringAnnotator;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiFile;

import javax.annotation.Nonnull;

/**
 * User : catherine
 * <p>
 * filter out some python inspections and annotations if we're in console
 */
@ExtensionImpl
public class ConsoleVisitorFilter implements PythonVisitorFilter
{
  @Override
  public boolean isSupported(@Nonnull final Class visitorClass, @Nonnull final PsiFile file) {
    //if we're in console
    if (PydevConsoleRunner.isInPydevConsole(file)) {
      //inspections
      if (visitorClass == PyUnusedLocalInspection.class || visitorClass == PyUnboundLocalVariableInspection.class ||
        visitorClass == PyStatementEffectInspection.class || visitorClass == PySingleQuotedDocstringInspection.class ||
        visitorClass == PyIncorrectDocstringInspection.class || visitorClass == PyMissingOrEmptyDocstringInspection.class ||
        visitorClass == PyMandatoryEncodingInspection.class || visitorClass == PyPep8Inspection.class) {
        return false;
      }

      //annotators
      if (visitorClass == DocStringAnnotator.class) {
        return false;
      }
    }
    return true;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
