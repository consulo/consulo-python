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

package com.jetbrains.python.impl.testing.pytest;

import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.OpenFileHyperlinkInfo;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User : catherine
 */
public class PyTestTracebackFilter implements Filter {
  private final Project myProject;
  private final String myWorkingDirectory;
  private final Pattern myMatchingPattern = Pattern.compile("([^\"]+):(\\d+)");

  public PyTestTracebackFilter(Project project, @Nullable String workingDirectory) {
    myProject = project;
    myWorkingDirectory = workingDirectory;
  }

  public Result applyFilter(String line, int entireLength) {
    //   C:\Progs\Crack\psidc\scummdc.py:72: AssertionError
    Matcher matcher = myMatchingPattern.matcher(line);
    if (matcher.find()) {
      String fileName = matcher.group(1).replace('\\', '/');
      int lineNumber = Integer.parseInt(matcher.group(2));
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      if (vFile == null && !StringUtil.isEmptyOrSpaces(myWorkingDirectory)) {
        vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(myWorkingDirectory, fileName)); 
      }
      
      if (vFile != null) {
        OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(myProject, vFile, lineNumber - 1);
        final int textStartOffset = entireLength - line.length();
        int startPos = 0;
        int endPos = line.lastIndexOf(':');
        return new Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink);
      }
    }
    return null;
  }
}
