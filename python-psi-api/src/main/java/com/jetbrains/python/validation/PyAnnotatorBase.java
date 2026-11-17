package com.jetbrains.python.validation;

import com.jetbrains.python.ast.PyAstFile;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

public abstract class PyAnnotatorBase implements Annotator {
  @Override
  public final void annotate(PsiElement element, AnnotationHolder holder) {
    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    if (!(file instanceof PyAstFile pyFile && !pyFile.isAcceptedFor(getClass()))) {
      annotate(element, new PyAnnotationHolder(holder));
    }
  }

  protected abstract void annotate(PsiElement element, PyAnnotationHolder holder);
}
