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

package com.jetbrains.python.impl.refactoring.classes.pushDown;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import com.jetbrains.python.psi.PyClass;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownUsageViewDescriptor implements UsageViewDescriptor {
  private final PyClass myClass;
  private static final String HEADER = RefactoringBundle.message("push.down.members.elements.header");

  public PyPushDownUsageViewDescriptor(PyClass clazz) {
    myClass = clazz;
  }

  @Nonnull
  public PsiElement[] getElements() {
    return new PsiElement[] {myClass};
  }

  public String getProcessedElementsHeader() {
    return HEADER;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Nullable
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}
