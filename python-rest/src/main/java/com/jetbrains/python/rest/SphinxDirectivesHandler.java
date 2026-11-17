// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class SphinxDirectivesHandler {
  private SphinxDirectivesHandler() {
  }

  /**
   * Filters out errors and displays unknown reStructuredText directives verbatim.
   */
  public static String applySphinx(String output) {
    Document html = Jsoup.parse(output);
    for (Element systemMessageElement : html.getElementsByClass("system-message")) {
      Element literalBlockElement = systemMessageElement.getElementsByClass("literal-block").first();
      if (literalBlockElement != null) {
        systemMessageElement.replaceWith(literalBlockElement);
      }
      else {
        systemMessageElement.remove();
      }
    }
    String problematicClassName = "problematic";
    for (Element problematicElement : html.getElementsByClass(problematicClassName)) {
      problematicElement.removeClass(problematicClassName);
    }
    return html.toString();
  }
}
