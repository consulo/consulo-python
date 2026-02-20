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

/*
 * @author max
 */
package com.jetbrains.python.impl.refactoring;

import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.rename.NameSuggestionProvider;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */
@ExtensionImpl
public class PyNameSuggestionProvider implements NameSuggestionProvider {
  public SuggestedNameInfo getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, Set<String> result) {
    if (!(element instanceof PyElement)) return null;
    String name = ((PyElement)element).getName();
    if (name == null) return null;

    if (element instanceof PyClass) {
      result.add(toCamelCase(name, true));
    }
    else if (element instanceof PyFunction || element instanceof PyParameter) {
      result.add(name.toLowerCase());
    }
    else {
      result.add(name.toLowerCase());
      PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
      if (assignmentStatement != null) return null;
      result.add(name.toUpperCase());
      result.add(toCamelCase(name, false));
    }
    return SuggestedNameInfo.NULL_INFO;
  }

  @Nonnull
  protected String toCamelCase(@Nonnull String name, boolean uppercaseFirstLetter) {
    List<String> strings = StringUtil.split(name, "_");
    if (strings.size() > 0) {
      StringBuilder buf = new StringBuilder();
      String str = strings.get(0).toLowerCase();
      if (uppercaseFirstLetter) str = StringUtil.capitalize(str);
      buf.append(str);
      for (int i = 1; i < strings.size(); i++) {
        buf.append(StringUtil.capitalize(strings.get(i).toLowerCase()));
      }
      return buf.toString();
    }
    return name;
  }
}
