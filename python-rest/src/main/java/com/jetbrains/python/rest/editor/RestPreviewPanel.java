// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.editor;

import consulo.disposer.Disposable;

import javax.swing.JComponent;

/**
 * Every reStructuredText editor preview panel must implement this interface.
 */
public interface RestPreviewPanel extends Disposable {

  void setHtml(String html);

  void render();

  /**
   * Returns the component that actually displays the rendered HTML and shown in the interface.
   */
  JComponent getComponent();
}
