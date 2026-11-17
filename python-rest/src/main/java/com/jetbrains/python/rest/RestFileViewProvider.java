// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.rest.RestLanguage;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.impl.file.MultiplePsiFilesPerDocumentFileViewProvider;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.parser.ParserDefinition;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.template.TemplateLanguageFileViewProvider;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * User : catherine
 */
public class RestFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
  implements TemplateLanguageFileViewProvider {

  private @Nullable Set<Language> myLanguages;

  public RestFileViewProvider(PsiManager manager, VirtualFile virtualFile, boolean physical) {
    super(manager, virtualFile, physical);
  }

  @Override
  public Language getBaseLanguage() {
    return RestLanguage.INSTANCE;
  }

  @Override
  public Language getTemplateDataLanguage() {
    return PythonLanguage.getInstance();
  }

  @Override
  protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner(VirtualFile virtualFile) {
    return new RestFileViewProvider(getManager(), virtualFile, false);
  }

  @Override
  public Set<Language> getLanguages() {
    if (myLanguages == null) {
      Set<Language> languages = new HashSet<>();
      languages.add(getBaseLanguage());
      Language djangoTemplateLanguage = Language.findLanguageByID("DjangoTemplate");
      if (djangoTemplateLanguage != null) {
        languages.add(djangoTemplateLanguage);
      }
      languages.add(getTemplateDataLanguage());
      myLanguages = languages;
    }
    return myLanguages;
  }

  @Override
  protected @Nullable PsiFile createFile(Language lang) {
    ParserDefinition def = ParserDefinition.forLanguage(lang);
    if (def == null) {
      return null;
    }
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

  @Override
  public @Nullable IElementType getContentElementType(Language language) {
    if (language == getTemplateDataLanguage()) {
      return RestPythonElementTypes.PYTHON_BLOCK_DATA;
    }
    else if (language.getID().equals("DjangoTemplate")) {
      return RestPythonElementTypes.DJANGO_BLOCK_DATA;
    }
    return null;
  }
}
