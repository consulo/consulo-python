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

import com.jetbrains.python.psi.WeakFileReference;
import consulo.document.util.TextRange;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.path.FileReferenceHelper;
import consulo.language.psi.path.FileReferenceHelperRegistrar;
import consulo.language.psi.path.FileReferenceSet;
import consulo.language.psi.path.PsiFileSystemItemUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

/**
 * @author yole
 */
public class TemplateFileReference extends WeakFileReference {
  public TemplateFileReference(@Nonnull FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    super(fileReferenceSet, range, index, text);
  }

  @Nullable
  @Override
  public String getUnresolvedDescription() {
    return "Template file '" + getCanonicalText() + "' not found";
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element, boolean absolute) throws IncorrectOperationException {
    if (!(element instanceof PsiFileSystemItem)) {
      throw new IncorrectOperationException("Cannot bind to element, should be instanceof PsiFileSystemItem: " + element);
    }

    PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)element;
    VirtualFile dstVFile = fileSystemItem.getVirtualFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

    PsiFile file = getElement().getContainingFile();
    PsiElement contextPsiFile = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file);
    if (contextPsiFile != null) file = contextPsiFile.getContainingFile(); // use host file!
    VirtualFile curVFile = file.getVirtualFile();
    if (curVFile == null) throw new IncorrectOperationException("Cannot bind from non-physical element:" + file);

    Project project = element.getProject();

    String newName;

    PsiFileSystemItem curItem = null;
    PsiFileSystemItem dstItem = null;

    FileReferenceHelper helper = FileReferenceHelperRegistrar.getNotNullHelper(file);

    PsiFileSystemItem _dstItem = helper.getPsiFileSystemItem(project, dstVFile);
    PsiFileSystemItem _curItem = helper.getPsiFileSystemItem(project, curVFile);
    if (_dstItem != null && _curItem != null) {
      curItem = _curItem;
      dstItem = _dstItem;
    }

    Collection<PsiFileSystemItem> contexts = getContexts();
    switch (contexts.size()) {
      case 0:
        break;
      default:
        for (PsiFileSystemItem context : contexts) {
          VirtualFile contextFile = context.getVirtualFile();
          assert contextFile != null;
          if (VirtualFileUtil.isAncestor(contextFile, dstVFile, true)) {
            String path = VirtualFileUtil.getRelativePath(dstVFile, contextFile, '/');
            if (path != null) {
              return rename(path.replace("/", getFileReferenceSet().getSeparatorString()));
            }
          }
        }
    }
    if (curItem == null) {
      throw new IncorrectOperationException("Cannot find path between files; " +
                                            "src = " + curVFile.getPresentableUrl() + "; " +
                                            "dst = " + dstVFile.getPresentableUrl() + "; " +
                                            "Contexts: " + contexts);
    }
    if (curItem.equals(dstItem)) {
      if (getCanonicalText().equals(dstItem.getName())) {
        return getElement();
      }
      return ElementManipulators.getManipulator(getElement()).handleContentChange(getElement(), getRangeInElement(), file.getName());
    }
    newName = PsiFileSystemItemUtil.getRelativePath(curItem, dstItem);
    if (newName == null) {
      return getElement();
    }

    return rename(newName);
  }
}
