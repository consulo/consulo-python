// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.wolfAnalyzer.WolfFileProblemFilter;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;

/**
 * @author yole
 */
@ExtensionImpl
public class PyProblemFileHighlightFilter implements WolfFileProblemFilter {
  @Override
  public boolean isToBeHighlighted(VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    return fileType == PythonFileType.INSTANCE;
  }
}
