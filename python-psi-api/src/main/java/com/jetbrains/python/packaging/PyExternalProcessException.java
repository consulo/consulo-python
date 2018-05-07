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

package com.jetbrains.python.packaging;

import com.intellij.openapi.util.text.StringUtil;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyExternalProcessException extends Exception {
  private static final Pattern WITH_CR_DELIMITER_PATTERN = Pattern.compile("(?<=\r|\n|\r\n)");

  private final int myRetcode;
  @Nonnull
  private String myName;
  @Nonnull
  private List<String> myArgs;
  @Nonnull
  private String myMessage;

  public PyExternalProcessException(int retcode, @Nonnull String name, @Nonnull List<String> args, @Nonnull String message) {
    super(String.format("External process error '%s %s':\n%s", name, StringUtil.join(args, " "), message));
    myRetcode = retcode;
    myName = name;
    myArgs = args;
    myMessage = stripLinesWithoutLineFeeds(message);
  }

  public PyExternalProcessException(int retcode, @Nonnull String name, @Nonnull List<String> args, @Nonnull String message, Throwable cause) {
    super(String.format("External process error '%s %s':\n%s", name, StringUtil.join(args, " "), message), cause);
    myRetcode = retcode;
    myName = name;
    myArgs = args;
    myMessage = stripLinesWithoutLineFeeds(message);
  }

  @Override
  public String toString() {
    final StringBuilder b = new StringBuilder();
    b.append("The following command was executed:\n\n");
    final String command = getName() + " " + StringUtil.join(getArgs(), " ");
    b.append(command);
    b.append("\n\n");
    b.append("The error output of the command:\n\n");
    b.append(getMessage());
    return b.toString();
  }

  public int getRetcode() {
    return myRetcode;
  }

  @Nonnull
  public String getName() {
    return myName;
  }

  @Nonnull
  public List<String> getArgs() {
    return myArgs;
  }

  @Nonnull
  public String getMessage() {
    return myMessage;
  }

  @Nonnull
  private static String stripLinesWithoutLineFeeds(@Nonnull String s) {
    final String[] lines = WITH_CR_DELIMITER_PATTERN.split(s);
    final List<String> result = new ArrayList<String>();
    for (String line : lines) {
      if (!line.endsWith("\r")) {
        result.add(line);
      }
    }
    return StringUtil.join(result, "");
  }
}
