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

package com.jetbrains.python.buildout.config;

import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import java.util.HashMap;

/**
 * @author traff
 */
public class BuildoutCfgColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
    new AttributesDescriptor("Section name", BuildoutCfgSyntaxHighlighter.BUILDOUT_SECTION_NAME),
    new AttributesDescriptor("Key", BuildoutCfgSyntaxHighlighter.BUILDOUT_KEY),
    new AttributesDescriptor("Value", BuildoutCfgSyntaxHighlighter.BUILDOUT_VALUE),
    new AttributesDescriptor("Key value separator", BuildoutCfgSyntaxHighlighter.BUILDOUT_KEY_VALUE_SEPARATOR),
    new AttributesDescriptor("Comment", BuildoutCfgSyntaxHighlighter.BUILDOUT_COMMENT)
  };

  @NonNls private static final HashMap<String, TextAttributesKey> ourTagToDescriptorMap = new HashMap<String, TextAttributesKey>();

  static {
    //ourTagToDescriptorMap.put("comment", DjangoTemplateHighlighterColors.DJANGO_COMMENT);
  }

  @Nonnull
  public String getDisplayName() {
    return "Buildout config";
  }

  @Nonnull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @Nonnull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Nonnull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(BuildoutCfgFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @Nonnull
  public String getDemoText() {
    return
      "; Buildout config\n"+
      "[buildout]\n" +
      "parts = python\n" +
      "develop = .\n" +
      "eggs = django-shorturls\n" +
      "\n" +
      "[python]\n" +
      "recipe = zc.recipe.egg\n" +
      "interpreter = python\n" +
      "eggs = ${buildout:eggs}";
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
