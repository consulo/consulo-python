/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid;

import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.psi.PyElementGenerator;

public abstract class PythonLanguage extends Language
{
  PythonLanguage(String id)
  {
    super(id);
  }

  PythonLanguage(String ID, String[] mimeTypes) {
    super(ID, mimeTypes);
  }
  @NotNull
  public Annotator getAnnotator() {
    throw new IllegalStateException();
  }

  public abstract PythonReferenceProviderRegistry getReferenceProviderRegistry();

  public abstract PyTokenTypes getTokenTypes();

  public abstract PyElementTypes getElementTypes();

  public abstract FileType getFileType();

  public abstract FileCreator getFileCreator();

  public abstract PyElementGenerator getElementGenerator();
}