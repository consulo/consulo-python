/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.codeInsight.completion;

import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * TODO: Add description
 * User: dcheryasov
 * Date: Mar 2, 2010 5:05:24 PM
 */
public class PythonLookupElement extends LookupElement implements Comparable<LookupElement> {

  protected final String myLookupString;
  protected final String myTypeText;
  protected final boolean isBold;
  protected final Image myIcon;
  private final Image myTypeIcon;
  protected final String myTailText;
  protected InsertHandler<PythonLookupElement> myHandler;

  public PythonLookupElement(@Nonnull final String lookupString,
                             @Nullable final String tailText,
                             @Nullable final String typeText, final boolean bold,
                             @Nullable final Image icon,
                             @Nullable final Image typeIcon,
                             @Nonnull final InsertHandler<PythonLookupElement> handler) {
    myLookupString = lookupString;
    myTailText = tailText;
    myTypeText = typeText;
    isBold = bold;
    myIcon = icon;
    myTypeIcon = typeIcon;
    myHandler = handler;
  }

  public PythonLookupElement(@Nonnull final String lookupString,
                             @Nullable final String tailText,
                             @Nullable final String typeText, final boolean bold,
                             @Nullable final Image icon,
                             @Nullable final Image typeIcon) {
    this(lookupString, tailText, typeText, bold, icon, typeIcon, (context, item) -> {});
  }

  public PythonLookupElement(
    @Nonnull final String lookupString,
    final boolean bold,
    @Nullable final Image icon
  ) {
    this(lookupString, null, null, bold, icon, null, (context, item) -> {});
  }

  @Nonnull
  public String getLookupString() {
    return myLookupString;
  }

  @Nullable
  public String getTailText() {
    return !StringUtil.isEmpty(myTailText) ? myTailText : null;
  }

  @Nullable
  protected String getTypeText() {
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

  public int compareTo(final LookupElement o) {
    return myLookupString.compareTo(o.getLookupString());
  }

}

