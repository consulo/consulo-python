// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;

import org.jspecify.annotations.Nullable;

/**
 * TODO: Add description
 */
public class PythonLookupElement extends LookupElement implements Comparable<LookupElement> {

  protected final String myLookupString;
  protected final String myTypeText;
  protected final boolean isBold;
  protected final Image myIcon;
  private final Image myTypeIcon;
  protected final String myTailText;
  protected InsertHandler<PythonLookupElement> myHandler;

  public PythonLookupElement(String lookupString,
                             @Nullable String tailText,
                             @Nullable String typeText, boolean bold,
                             @Nullable Image icon,
                             @Nullable Image typeIcon,
                             InsertHandler<PythonLookupElement> handler) {
    myLookupString = lookupString;
    myTailText = tailText;
    myTypeText = typeText;
    isBold = bold;
    myIcon = icon;
    myTypeIcon = typeIcon;
    myHandler = handler;
  }

  public PythonLookupElement(String lookupString,
                             @Nullable String tailText,
                             @Nullable String typeText, boolean bold,
                             @Nullable Image icon,
                             @Nullable Image typeIcon) {
    // BasicInsertHandler (empty handler) is missing in Consulo
    this(lookupString, tailText, typeText, bold, icon, typeIcon, (context, item) -> {});
  }

  public PythonLookupElement(
    String lookupString,
    boolean bold,
    @Nullable Image icon
  ) {
    this(lookupString, null, null, bold, icon, null, (context, item) -> {});
  }

  @Override
  public String getLookupString() {
    return myLookupString;
  }

  public @Nullable String getTailText() {
    return !StringUtil.isEmpty(myTailText) ? myTailText : null;
  }

  protected @Nullable String getTypeText() {
    return !StringUtil.isEmpty(myTypeText) ? myTypeText : null;
  }

  public Image getIcon() {
    return myIcon;
  }


  public Image getTypeIcon() {
    return myTypeIcon;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    myHandler.handleInsert(context, this);
  }

  public void setHandler(InsertHandler<PythonLookupElement> handler) {
    myHandler = handler;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
    presentation.setItemTextBold(isBold);
    presentation.setTailText(getTailText());
    presentation.setTypeText(getTypeText(), getTypeIcon());
    presentation.setIcon(getIcon());
  }

  @Override
  public int compareTo(LookupElement o) {
    return myLookupString.compareTo(o.getLookupString());
  }
}

