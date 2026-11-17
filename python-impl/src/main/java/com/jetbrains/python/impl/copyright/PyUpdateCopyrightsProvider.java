// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.copyright;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.copyright.BaseUpdateCopyrightsProvider;
import consulo.language.copyright.UpdatePsiFileCopyright;
import consulo.language.copyright.config.CopyrightFileConfig;
import consulo.language.copyright.config.CopyrightProfile;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.virtualFileSystem.fileType.FileType;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@ExtensionImpl
public final class PyUpdateCopyrightsProvider extends BaseUpdateCopyrightsProvider {
  private static final Predicate<String> ENCODING_PATTERN =
    Pattern.compile("^[ \\t\\f]*#.*?coding[:=][ \\t]*([-_.a-zA-Z0-9]+)").asPredicate();

  @Override
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  public UpdatePsiFileCopyright<CopyrightFileConfig> createInstance(PsiFile file, CopyrightProfile copyrightProfile) {
    return new PyUpdateFileCopyright(file, copyrightProfile);
  }

  @Override
  public CopyrightFileConfig createDefaultOptions() {
    CopyrightFileConfig options = super.createDefaultOptions();
    options.setBlock(false);
    options.setFileTypeOverride(CopyrightFileConfig.USE_TEXT);
    return options;
  }

  private static final class PyUpdateFileCopyright extends UpdatePsiFileCopyright<CopyrightFileConfig> {
    PyUpdateFileCopyright(PsiFile file, CopyrightProfile options) {
      super(file, options);
    }

    @Override
    protected boolean accept() {
      return getFile() instanceof PyFile;
    }

    @Override
    protected void scanFile() {
      PyFile f = (PyFile)getFile();

      PsiElement first = f.getFirstChild() != null ? first(f) : f;

      for (List<? extends PsiElement> block : List.<List<? extends PsiElement>>of(f.getImportBlock(), f.getTopLevelClasses(), f.getTopLevelAttributes(),
                                                      f.getTopLevelFunctions())) {
        if (block.size() > 0) {
          checkComments(first, block.get(0), true);
          return;
        }
      }

      checkComments(first, f, true);
    }
  }

  private static PsiElement first(PyFile file) {
    return coding(shebang(file.getFirstChild()));
  }

  private static boolean isShebangComment(PsiElement element) {
    return element instanceof PsiComment && element.getText().startsWith("#!");
  }

  private static PsiElement shebang(PsiElement element) {
    if (isShebangComment(element)) {
      return skipWhitespaceForwardNotNull(element.getNextSibling());
    }
    else {
      return element;
    }
  }

  private static PsiElement skipWhitespaceForwardNotNull(PsiElement e) {
    PsiElement next = PsiTreeUtil.skipWhitespacesForward(e);
    return next != null ? next : e;
  }

  private static boolean isEncodingComment(PsiElement element) {
    return element instanceof PsiComment && ENCODING_PATTERN.test(element.getText());
  }

  private static PsiElement coding(PsiElement element) {
    if (isEncodingComment(element)) {
      return skipWhitespaceForwardNotNull(element.getNextSibling());
    }
    else {
      return element;
    }
  }
}
