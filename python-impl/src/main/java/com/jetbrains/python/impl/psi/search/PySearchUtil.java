// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.impl.magicLiteral.PyMagicLiteralTools;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.util.io.FileUtil;

public class PySearchUtil extends PySearchUtilBase {
  /**
   * Returns string that represents element in string search.
   *
   * @param element element to search
   * @return string that represents element
   */
  public static String computeElementNameForStringSearch(final PsiElement element) {
    if (element instanceof PyFile) {
      return FileUtil.getNameWithoutExtension(((PyFile)element).getName());
    }
    if (element instanceof PsiDirectory) {
      return ((PsiDirectory)element).getName();
    }
    // Magic literals are always represented by their string values
    if ((element instanceof PyStringLiteralExpression) && PyMagicLiteralTools.couldBeMagicLiteral(element)) {
      return ((StringLiteralExpression)element).getStringValue();
    }
    if (element instanceof PyElement) {
      final String name = ((PyElement)element).getName();
      if (name != null) {
        return name;
      }
    }
    return element.getNode() != null ? element.getNode().getText() : element.getText();
  }
}
