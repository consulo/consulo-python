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

package com.jetbrains.python.psi;

import consulo.language.psi.path.FileReference;
import consulo.language.psi.path.FileReferenceHelper;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.CachingReference;
import consulo.language.psi.path.FileReferenceSet;
import consulo.language.util.IncorrectOperationException;
import javax.annotation.Nonnull;

import java.util.Collection;

/**
 * This is a temporary fix for FileReference.bindToElement to take contexts from FileReference.getContexts() instead for helpers.
 *
 * @author traff
 */
public class FileReferenceWithOneContext extends FileReference
{

  public FileReferenceWithOneContext(@Nonnull FileReferenceSet fileReferenceSet,
                                     TextRange range, int index, String text) {
    super(fileReferenceSet, range, index, text);
  }

  public FileReferenceWithOneContext(FileReference original) {
    super(original);
  }

  @Override
  protected Collection<PsiFileSystemItem> getContextsForBindToElement(VirtualFile curVFile, Project project, FileReferenceHelper helper) {
    return getContexts();
  }

  @Override
  protected PsiElement rename(final String newName) throws IncorrectOperationException {
    if (FileUtil.isAbsolutePlatformIndependent(newName)) {
      return super.rename(newName);
    }
    else {
      PsiElement element = getElement();
      return CachingReference.getManipulator(element).handleContentChange(element, getRangeInElement(), newName);
    }
  }
}
