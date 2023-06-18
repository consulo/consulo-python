/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.userSkeletons;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.language.Language;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.GutterIconNavigationHandler;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiNavigateUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
@ExtensionImpl
public class PyUserSkeletonsLineMarkerProvider implements LineMarkerProvider {
  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@Nonnull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@Nonnull List<PsiElement> elements, @Nonnull Collection<LineMarkerInfo> result) {
    for (PsiElement element : elements) {
      final PyElement skeleton = getUserSkeleton(element);
      if (skeleton != null) {
        result.add(new LineMarkerInfo<PsiElement>(element,
                                                  element.getTextRange(),
                                                  AllIcons.Gutter.Unique,
                                                  Pass.LINE_MARKERS,
                                                  e -> "Has user skeleton",
                                                  new GutterIconNavigationHandler<PsiElement>() {
                                                    @Override
                                                    public void navigate(MouseEvent e, PsiElement elt) {
                                                      final PyElement s = getUserSkeleton(elt);
                                                      if (s != null) {
                                                        PsiNavigateUtil.navigate(s);
                                                      }
                                                    }
                                                  },
                                                  GutterIconRenderer.Alignment.RIGHT));
      }
    }
  }

  @Nullable
  private static PyElement getUserSkeleton(@Nonnull PsiElement element) {
    if (element instanceof PyFunction || element instanceof PyTargetExpression) {
      return PyUserSkeletonsUtil.getUserSkeleton((PyElement)element);
    }
    return null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
