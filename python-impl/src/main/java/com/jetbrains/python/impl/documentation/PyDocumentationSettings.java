/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.ide.impl.idea.openapi.module.ModuleServiceManager;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.XmlSerializerUtil;
import consulo.util.xml.serializer.annotation.OptionTag;
import consulo.util.xml.serializer.annotation.Transient;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author yole
 */
@State(name = "PyDocumentationSettings",
  storages = {@Storage(file = "$MODULE_FILE$")})
@ServiceAPI(ComponentScope.MODULE)
@ServiceImpl
@Singleton
public class PyDocumentationSettings implements PersistentStateComponent<PyDocumentationSettings> {
  public static final DocStringFormat DEFAULT_DOCSTRING_FORMAT = DocStringFormat.REST;

  public static PyDocumentationSettings getInstance(@Nonnull Module module) {
    return ModuleServiceManager.getService(module, PyDocumentationSettings.class);
  }

  @Nonnull
  private DocStringFormat myDocStringFormat = DEFAULT_DOCSTRING_FORMAT;
  private boolean myAnalyzeDoctest = true;

  public boolean isNumpyFormat(PsiFile file) {
    return isFormat(file, DocStringFormat.NUMPY);
  }

  public boolean isPlain(PsiFile file) {
    return isFormat(file, DocStringFormat.PLAIN);
  }

  private boolean isFormat(@Nullable PsiFile file, @Nonnull DocStringFormat format) {
    return file instanceof PyFile ? getFormatForFile(file) == format : myDocStringFormat == format;
  }

  @Nonnull
  public DocStringFormat getFormatForFile(@Nonnull PsiFile file) {
    final DocStringFormat fileFormat = getFormatFromDocformatAttribute(file);
    return fileFormat != null && fileFormat != DocStringFormat.PLAIN ? fileFormat : myDocStringFormat;
  }

  @Nullable
  public static DocStringFormat getFormatFromDocformatAttribute(@Nonnull PsiFile file) {
    if (file instanceof PyFile) {
      final PyTargetExpression expr = ((PyFile)file).findTopLevelAttribute(PyNames.DOCFORMAT);
      if (expr != null) {
        final String docformat = PyPsiUtils.strValue(expr.findAssignedValue());
        if (docformat != null) {
          final List<String> words = StringUtil.split(docformat, " ");
          if (words.size() > 0) {
            final DocStringFormat fileFormat = DocStringFormat.fromName(words.get(0));
            if (fileFormat != null) {
              return fileFormat;
            }
          }
        }
      }
    }
    return null;
  }

  @Transient
  @Nonnull
  public DocStringFormat getFormat() {
    return myDocStringFormat;
  }

  public void setFormat(@Nonnull DocStringFormat format) {
    myDocStringFormat = format;
  }

  // Legacy name of the field to preserve settings format
  @SuppressWarnings("unused")
  @OptionTag("myDocStringFormat")
  @Nonnull
  public String getFormatName() {
    return myDocStringFormat.getName();
  }

  @SuppressWarnings("unused")
  public void setFormatName(@Nonnull String name) {
    myDocStringFormat = DocStringFormat.fromNameOrPlain(name);
  }

  public boolean isAnalyzeDoctest() {
    return myAnalyzeDoctest;
  }

  public void setAnalyzeDoctest(boolean analyze) {
    myAnalyzeDoctest = analyze;
  }

  @Override
  public PyDocumentationSettings getState() {
    return this;
  }

  @Override
  public void loadState(PyDocumentationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
