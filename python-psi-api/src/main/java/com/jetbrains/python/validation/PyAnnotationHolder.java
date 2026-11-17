package com.jetbrains.python.validation;

import consulo.application.ApplicationManager;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;

import static consulo.language.editor.rawHighlight.HighlightInfoType.SYMBOL_TYPE_SEVERITY;

public final class PyAnnotationHolder {
  private final boolean myTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private final AnnotationHolder myHolder;

  public PyAnnotationHolder(AnnotationHolder holder) { myHolder = holder; }

  public boolean isBatchMode() { return myHolder.isBatchMode(); }

  public AnnotationBuilder newAnnotation(HighlightSeverity severity, String message) {
    return myHolder.newAnnotation(severity, message);
  }

  public AnnotationBuilder newSilentAnnotation(HighlightSeverity severity) {
    return myHolder.newSilentAnnotation(severity);
  }

  public void markError(PsiElement element, String message) {
    myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(element).create();
  }

  public void addHighlightingAnnotation(PsiElement target, TextAttributesKey key) {
    addHighlightingAnnotation(target, key, SYMBOL_TYPE_SEVERITY);
  }

  public void addHighlightingAnnotation(PsiElement target,
                                         TextAttributesKey key,
                                         HighlightSeverity severity) {
    addHighlightingAnnotation(target.getTextRange(), key, severity);
  }

  public void addHighlightingAnnotation(TextRange range,
                                        TextAttributesKey key,
                                        HighlightSeverity severity) {
    final String message = myTestMode ? key.getExternalName() : null;
    // CodeInsightTestFixture#testHighlighting doesn't consider annotations with severity level < INFO
    final HighlightSeverity actualSeverity =
      myTestMode && severity.myVal < HighlightSeverity.INFORMATION.myVal ? HighlightSeverity.INFORMATION : severity;
    (message == null ? myHolder.newSilentAnnotation(actualSeverity) : myHolder.newAnnotation(actualSeverity, message))
      .range(range).textAttributes(key).create();
  }

  public void addHighlightingAnnotation(ASTNode target, TextAttributesKey key) {
    addHighlightingAnnotation(target.getPsi(), key);
  }
}
