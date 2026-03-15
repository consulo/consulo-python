package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class PyDumbAwareAnnotatorFactory implements AnnotatorFactory, DumbAware {
  @Nullable
  @Override
  public Annotator createAnnotator() {
    return new PyDumbAwareAnnotator();
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
