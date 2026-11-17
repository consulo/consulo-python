// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.editor;

import consulo.ui.ex.awt.JBScrollPane;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.html.HTMLEditorKit;

public class RestSwingHtmlPanel implements RestPreviewPanel {

  private final JTextPane myPane;
  private final JScrollPane myScrollPane;

  RestSwingHtmlPanel() {
    myPane = new JTextPane();
    myPane.setEditorKit(new HTMLEditorKit());
    myScrollPane = new JBScrollPane(myPane);
  }

  @Override
  public void setHtml(String html) {
    final int body = html.indexOf("<body>");
    if (body > 0) {
      html = "<html>" + html.substring(body);
    }
    myPane.setText(html);
  }

  @Override
  public void render() {
    myPane.revalidate();
    myPane.repaint();
  }

  @Override
  public JComponent getComponent() {
    return myScrollPane;
  }

  @Override
  public void dispose() {
  }
}
