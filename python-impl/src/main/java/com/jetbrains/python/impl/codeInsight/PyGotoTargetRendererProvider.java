// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.PyElement;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.ide.navigation.GotoTargetPresentationProvider;
import consulo.language.editor.ui.navigation.PsiTargetPresentationFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.localize.LocalizeValue;
import consulo.navigation.ItemPresentation;
import consulo.navigation.NavigationItem;
import consulo.navigation.TargetPresentation;
import org.jspecify.annotations.Nullable;

/**
 * Consulo replaces JB's GotoTargetRendererProvider + PyElementListCellRenderer with
 * GotoTargetPresentationProvider; the element and container texts follow JB's PyElementListCellRenderer.
 */
@ExtensionImpl
public final class PyGotoTargetRendererProvider implements GotoTargetPresentationProvider {
  @Override
  @RequiredReadAction
  public @Nullable TargetPresentation getPresentation(PsiElement element, Options options) {
    if (element instanceof PyElement && element instanceof PsiNamedElement) {
      String containerText = getContainerText(element);
      return Application.get().getInstance(PsiTargetPresentationFactory.class)
        .presentationBuilder(element)
        .withPresentableText(LocalizeValue.of(getElementText(element)))
        .withContainerText(containerText == null ? LocalizeValue.empty() : LocalizeValue.of(containerText))
        .build();
    }
    return null;
  }

  // PyElementListCellRenderer.getElementText
  @RequiredReadAction
  private static String getElementText(PsiElement element) {
    if (element instanceof PsiNamedElement namedElement) {
      String name = namedElement.getName();
      return name == null ? "" : name;
    }
    return element.getText();
  }

  // PyElementListCellRenderer.getContainerText
  private static @Nullable String getContainerText(PsiElement element) {
    if (element instanceof NavigationItem navigationItem) {
      ItemPresentation presentation = navigationItem.getPresentation();
      if (presentation != null) {
        return presentation.getLocationString();
      }
    }
    return null;
  }
}
