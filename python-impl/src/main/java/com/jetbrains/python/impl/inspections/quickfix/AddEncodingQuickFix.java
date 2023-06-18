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

package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.inspections.PyEncodingUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;

/**
 * add missing encoding declaration
 * # -*- coding: <encoding name> -*-
 * to the source file
 *
 * User: catherine
 */
public class AddEncodingQuickFix implements LocalQuickFix {

  private String myDefaultEncoding;
  private int myEncodingFormatIndex;

  public AddEncodingQuickFix(String defaultEncoding, int encodingFormatIndex) {
    myDefaultEncoding = defaultEncoding;
    myEncodingFormatIndex = encodingFormatIndex;
  }

  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.add.encoding");
  }

  @NonNls
  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    PsiFile file = descriptor.getPsiElement().getContainingFile();
    if (file == null) return;
    PsiElement firstLine = file.getFirstChild();
    if (firstLine instanceof PsiComment && firstLine.getText().startsWith("#!")) {
      firstLine = firstLine.getNextSibling();
    }
    PsiComment encodingLine = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(file), PsiComment.class,
                                                                                     String.format(PyEncodingUtil.ENCODING_FORMAT_PATTERN[myEncodingFormatIndex], myDefaultEncoding));
    file.addBefore(encodingLine, firstLine);
  }
}
