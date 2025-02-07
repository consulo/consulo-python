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
package com.jetbrains.python.templateLanguages;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nullable;
import java.util.Collection;

/**
 * Returns the list of variables which are available in the context for a template.
 *
 * @author yole
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface TemplateContextProvider {
  ExtensionPointName<TemplateContextProvider> EP_NAME = ExtensionPointName.create(TemplateContextProvider.class);

  /**
   * Returns the context for a template. The lookup string of each returned LookupElement in the returned list is the visible
   * name of the variable; the object is the PsiElement declaring the variable.
   *
   * @param template the template file
   * @return the list of variables, or null if the template is not used in the context handled by this processor.
   */
  @Nullable
  Collection<LookupElement> getTemplateContext(PsiFile template);
}
