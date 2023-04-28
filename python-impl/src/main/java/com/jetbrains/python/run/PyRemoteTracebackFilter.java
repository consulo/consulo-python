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
package com.jetbrains.python.run;

import consulo.ide.impl.idea.remote.RemoteProcessControl;
import consulo.project.Project;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author traff
 */
public class PyRemoteTracebackFilter extends PythonTracebackFilter {
  private final RemoteProcessControl myHandler;

  public PyRemoteTracebackFilter(Project project, String workingDirectory, RemoteProcessControl remoteProcessHandler) {
    super(project, workingDirectory);

    myHandler = remoteProcessHandler;
  }

  @Override
  @Nullable
  protected VirtualFile findFileByName(@Nonnull String fileName) {
    VirtualFile vFile = super.findFileByName(fileName);
    if (vFile != null) {
      return vFile;
    }
    String localFile = myHandler.getMappingSettings().convertToLocal(fileName);
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(localFile);
    if (file != null && file.exists()) {
      return file;
    }
    return null;
  }
}
