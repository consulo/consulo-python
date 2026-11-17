/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import com.jetbrains.python.toolbox.Substring;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class SphinxDocString extends TagBasedDocString {
  public static final String[] KEYWORD_ARGUMENT_TAGS = new String[]{"keyword", "key"};
  public static final String[] ALL_TAGS = new String[]{":param", ":parameter", ":arg", ":argument", ":keyword", ":key",
    ":type", ":raise", ":raises", ":var", ":cvar", ":ivar",
    ":return", ":returns", ":rtype", ":except", ":exception"};
  public static final String TAG_PREFIX = ":";

  public SphinxDocString(Substring docstringText) {
    super(docstringText, TAG_PREFIX);
  }

  @Override
  public List<String> getKeywordArguments() {
    return toUniqueStrings(getKeywordArgumentSubstrings());
  }

  @Override
  public @Nullable String getKeywordArgumentDescription(@Nullable String paramName) {
    if (paramName == null) {
      return null;
    }
    return concatTrimmedLines(getTagValue(KEYWORD_ARGUMENT_TAGS, paramName));
  }

  @Override
  public @Nullable String getReturnType() {
    return concatTrimmedLines(getReturnTypeSubstring());
  }

  @Override
  public @Nullable String getParamType(@Nullable String paramName) {
    return concatTrimmedLines(getParamTypeSubstring(paramName));
  }

  @Override
  public @Nullable String getParamDescription(@Nullable String paramName) {
    return paramName != null ? concatTrimmedLines(getTagValue(PARAM_TAGS, paramName)) : null;
  }

  @Override
  public @Nullable String getReturnDescription() {
    return concatTrimmedLines(getTagValue(RETURN_TAGS));
  }

  @Override
  public List<String> getRaisedExceptions() {
    return toUniqueStrings(getTagArguments(RAISES_TAGS));
  }

  @Override
  public @Nullable String getRaisedExceptionDescription(@Nullable String exceptionName) {
    if (exceptionName == null) {
      return null;
    }
    return concatTrimmedLines(getTagValue(RAISES_TAGS, exceptionName));
  }

  @Override
  public @Nullable String getAttributeDescription() {
    return concatTrimmedLines(getTagValue(VARIABLE_TAGS));
  }

  @Override
  public List<Substring> getKeywordArgumentSubstrings() {
    return getTagArguments(KEYWORD_ARGUMENT_TAGS);
  }

  @Override
  public @Nullable Substring getReturnTypeSubstring() {
    return getTagValue("rtype");
  }

  @Override
  public @Nullable Substring getParamTypeSubstring(@Nullable String paramName) {
    return paramName == null ? getTagValue("type") : getTagValue("type", paramName);
  }

  @Override
  public String getDescription() {
    return myDescription.replaceAll("\n", "<br/>");
  }

  @Override
  public @Nullable String getAttributeDescription(@Nullable String attrName) {
    return attrName != null ? concatTrimmedLines(getTagValue(VARIABLE_TAGS, attrName)) : null;
  }

  private static @Nullable String concatTrimmedLines(@Nullable Substring s) {
    return s != null ? s.concatTrimmedLines(" ") : null;
  }
}
