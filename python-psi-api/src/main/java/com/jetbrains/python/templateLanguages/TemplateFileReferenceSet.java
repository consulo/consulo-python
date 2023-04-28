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

import consulo.language.psi.path.FileReference;
import consulo.language.psi.path.FileReferenceSet;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.util.lang.Pair;
import consulo.application.util.SystemInfo;
import consulo.document.util.TextRange;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReferenceProvider;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PythonStringUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class TemplateFileReferenceSet extends FileReferenceSet
{
  private final String mySeparatorString;

  public TemplateFileReferenceSet(PsiElement element, @Nullable PsiReferenceProvider provider) {
    this(str(element), element, provider);
  }

  public TemplateFileReferenceSet(String text, PsiElement element,
                                  @Nullable PsiReferenceProvider provider) {
    super(text, element, detectShift(element, text), provider,
          SystemInfo.isFileSystemCaseSensitive);
    mySeparatorString = detectSeparator(element);
    reparse();
  }

  private static String str(PsiElement element) {
    return PythonStringUtil.stripQuotesAroundValue(element.getText());
  }

  public static int detectShift(PsiElement element, String text) {
    String elementText = element.getText();
    int from = 0;
    Pair<String, String> quotes = PythonStringUtil.getQuotes(elementText);
    if (quotes != null) {
      from = quotes.first.length();
    }

    return elementText.indexOf(text, from);
  }

  public static String detectSeparator(PsiElement element) {
    String winSeparator;
    if (PythonStringUtil.isRawString(element.getText())) {
      winSeparator = "\\";
    }
    else {
      winSeparator = "\\\\";
    }
    return str(element).contains(winSeparator) ? winSeparator : "/";
  }

  @Override
  public String getSeparatorString() {
    if (mySeparatorString == null) {
      return super.getSeparatorString();
    }
    else {
      return mySeparatorString;
    }
  }

  @Nonnull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    List<PsiFileSystemItem> contexts = ContainerUtil.newArrayList();
    if (getPathString().startsWith("/") || getPathString().startsWith("\\")) {
      return contexts;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(getElement());
    if (module != null) {
      List<VirtualFile> templatesFolders = getRoots(module);
      for (VirtualFile folder : templatesFolders) {
        final PsiFileSystemItem directory = getPsiDirectory(module, folder);
        if (directory != null) {
          contexts.add(directory);
        }
      }
    }
    return contexts;
  }

  protected PsiFileSystemItem getPsiDirectory(Module module, VirtualFile folder) {
    return PsiManager.getInstance(module.getProject()).findDirectory(folder);
  }

  protected List<VirtualFile> getRoots(Module module) {
    return TemplatesService.getInstance(module).getTemplateFolders();
  }

  @Override
  public FileReference createFileReference(TextRange range, int index, String text) {
    return new TemplateFileReference(this, range, index, text);
  }
}
