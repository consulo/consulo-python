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

package com.jetbrains.python.impl.refactoring;

import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Alexey.Ivanov
 */
public class NameSuggesterUtil {
  private NameSuggesterUtil() {
  }

  private static String deleteNonLetterFromString(@Nonnull final String string) {
    Pattern pattern = Pattern.compile("[^a-zA-Z_]+");
    Matcher matcher = pattern.matcher(string);
    return matcher.replaceAll("_");
  }

  @Nonnull
  public static Collection<String> generateNames(@Nonnull String name) {
    name = StringUtil.decapitalize(deleteNonLetterFromString(StringUtil.unquoteString(name.replace('.', '_'))));
    if (name.startsWith("get")) {
      name = name.substring(3);
    }
    else if (name.startsWith("is")) {
      name = name.substring(2);
    }
    while (name.startsWith("_")) {
      name = name.substring(1);
    }
    final int length = name.length();
    final Collection<String> possibleNames = new LinkedHashSet<String>();
    for (int i = 0; i < length; i++) {
      if (Character.isLetter(name.charAt(i)) &&
          (i == 0 || name.charAt(i - 1) == '_' || (Character.isLowerCase(name.charAt(i - 1)) && Character.isUpperCase(name.charAt(i))))) {
        final String candidate = StringUtil.decapitalize(toUnderscoreCase(name.substring(i)));
        if (candidate.length() < 25) {
          possibleNames.add(candidate);
        }
      }
    }
    // prefer shorter names
    ArrayList<String> reversed = new ArrayList<String>(possibleNames);
    Collections.reverse(reversed);
    return reversed;
  }

  public static Collection<String> generateNamesByType(@Nonnull String name) {
    final Collection<String> possibleNames = new LinkedHashSet<String>();
    name = StringUtil.decapitalize(deleteNonLetterFromString(name.replace('.', '_')));
    name = toUnderscoreCase(name);
    possibleNames.add(name);
    possibleNames.add(name.substring(0, 1));
    return possibleNames;
  }

  @Nonnull
  public static String toUnderscoreCase(@Nonnull final String name) {
    StringBuilder buffer = new StringBuilder();
    final int length = name.length();

    for (int i = 0; i < length; i++) {
      final char ch = name.charAt(i);
      if (ch != '-') {
        buffer.append(Character.toLowerCase(ch));
      }
      else {
        buffer.append("_");
      }

      if (Character.isLetterOrDigit(ch)) {
        if (Character.isUpperCase(ch)) {
          if (i + 2 < length) {
            final char chNext = name.charAt(i + 1);
            final char chNextNext = name.charAt(i + 2);

            if (Character.isUpperCase(chNext) && Character.isLowerCase(chNextNext)) {

              buffer.append('_');
            }
          }
        }
        else if (Character.isLowerCase(ch) || Character.isDigit(ch)) {
          if (i + 1 < length) {
            final char chNext = name.charAt(i + 1);
            if (Character.isUpperCase(chNext)) {
              buffer.append('_');
            }
          }
        }
      }
    }
    return buffer.toString();
  }
}
