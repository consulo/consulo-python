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

package com.jetbrains.python.rest;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.rest.RestLanguage;
import consulo.language.Language;
import consulo.language.impl.file.MultiplePsiFilesPerDocumentFileViewProvider;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.parser.ParserDefinition;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.template.TemplateLanguageFileViewProvider;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User : catherine
 */
public class RestFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
  implements TemplateLanguageFileViewProvider {

  private Set<Language> myLanguages;

  public RestFileViewProvider(PsiManager manager, VirtualFile virtualFile, boolean physical) {
    super(manager, virtualFile, physical);
  }

  @Nonnull
  @Override
  public Language getBaseLanguage() {
    return RestLanguage.INSTANCE;
  }

  @Override
  @Nonnull
  public Language getTemplateDataLanguage() {
    return PythonLanguage.getInstance();
  }

  @Override
  protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner(VirtualFile virtualFile) {
    return new RestFileViewProvider(getManager(), virtualFile, false);
  }

  @Override
  @Nonnull
  public Set<Language> getLanguages() {
    if (myLanguages == null) {
      myLanguages = new LinkedHashSet<>();
      myLanguages.add(getBaseLanguage());
      Language djangoTemplateLanguage = Language.findLanguageByID("DjangoTemplate");
      if (djangoTemplateLanguage != null) {
        myLanguages.add(djangoTemplateLanguage);
      }
      myLanguages.add(getTemplateDataLanguage());
    }
    return myLanguages;
  }

  @Override
  protected PsiFile createFile(@Nonnull final Language lang) {
    ParserDefinition def = ParserDefinition.forLanguage(lang);
    if (def == null) return null;
    if (lang == getTemplateDataLanguage()) {
      PsiFileImpl file = (PsiFileImpl)def.createFile(this);
      file.setContentElementType(RestPythonElementTypes.PYTHON_BLOCK_DATA);
      return file;
    }
    else if (lang.getID().equals("DjangoTemplate")) {
      PsiFileImpl file = (PsiFileImpl)def.createFile(this);
      file.setContentElementType(RestPythonElementTypes.DJANGO_BLOCK_DATA);
      return file;
    }
    else if (lang == RestLanguage.INSTANCE) {
      return def.createFile(this);
    }
    return null;
  }
}
