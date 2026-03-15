package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class PyAnnotatingVisitorFactory implements AnnotatorFactory {
  @Nullable
  @Override
  public Annotator createAnnotator() {
    return new PyAnnotatingVisitor();
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
