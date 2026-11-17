package com.jetbrains.python.pyi;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

public final class PyiUtilCore {
  public static boolean isInsideStub(PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null && file.getFileType() == PyiFileType.INSTANCE;
  }
}
