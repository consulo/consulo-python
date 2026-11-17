// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.documentation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.PyAstElsePart;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstIfPart;
import com.jetbrains.python.ast.PyAstTargetExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.StubElement;
import consulo.module.Module;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.OptionTag;
import org.jspecify.annotations.Nullable;

import java.util.List;


public abstract class PyDocumentationSettings
  extends PyDefaultProjectAwareService<PyDocumentationSettings.ServiceState,
          PyDocumentationSettings,
          PyDocumentationSettingsAppService,
          PyDocumentationSettingsModuleService> {

  static final String MODULE_STATE_COMPONENT = "PyDocumentationSettings";

  public static final PyDefaultProjectAwareServiceClasses<ServiceState, PyDocumentationSettings, PyDocumentationSettingsAppService, PyDocumentationSettingsModuleService, PyDocumentationSettingsFactory>
    SERVICE_CLASSES = new PyDefaultProjectAwareServiceClasses<>(PyDocumentationSettingsAppService.class, PyDocumentationSettingsFactory.class);

  public static final DocStringFormat DEFAULT_DOC_STRING_FORMAT = DocStringFormat.REST;

  protected PyDocumentationSettings() {
    super(new ServiceState());
  }

  protected PyDocumentationSettings(Module module) {
    super(new ServiceState(), MODULE_STATE_COMPONENT, ServiceState.class, module);
  }

  public static PyDocumentationSettings getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }


  public final boolean isNumpyFormat(PsiFile file) {
    return isFormat(file, DocStringFormat.NUMPY);
  }

  public final boolean isPlain(PsiFile file) {
    return isFormat(file, DocStringFormat.PLAIN);
  }

  private boolean isFormat(@Nullable PsiFile file, DocStringFormat format) {
    return file instanceof PyAstFile ? getFormatForFile(file) == format : getState().getFormat() == format;
  }

  public final DocStringFormat getFormatForFile(PsiFile file) {
    final DocStringFormat fileFormat = getFormatFromDocformatAttribute(file);
    return fileFormat != null && fileFormat != DocStringFormat.PLAIN ? fileFormat : getState().myDocStringFormat;
  }

  public static @Nullable DocStringFormat getFormatFromDocformatAttribute(PsiFile file) {
    if (file instanceof PyAstFile) {
      final PyAstTargetExpression expr = getDocFormatAttribute(file);
      if (expr != null) {
        final String docformat = PyPsiUtilsCore.strValue(expr.findAssignedValue());
        if (docformat != null) {
          final List<String> words = StringUtil.split(docformat, " ");
          if (!words.isEmpty()) {
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

  private static @Nullable PyAstTargetExpression getDocFormatAttribute(PsiFile file) {
    StubElement stub = ((PsiFileImpl)file).getGreenStub();
    if (stub != null) {
      return getDocFormatAttribute(stub.getChildrenStubs());
    }
    else {
      return getDocFormatAttribute(file.getChildren());
    }
  }

  private static @Nullable PyAstTargetExpression getDocFormatAttribute(List<StubElement> stubs) {
    for (StubElement<?> stub : stubs) {
      PsiElement psi = stub.getPsi();
      if (psi instanceof PyAstTargetExpression targetExpression && PyNames.DOCFORMAT.equals(targetExpression.getName())) {
        return targetExpression;
      }
      if (psi instanceof PyAstIfPart || psi instanceof PyAstElsePart) {
        PyAstTargetExpression targetExpression = getDocFormatAttribute(stub.getChildrenStubs());
        if (targetExpression != null) return targetExpression;
      }
    }
    return null;
  }

  private static @Nullable PyAstTargetExpression getDocFormatAttribute(PsiElement [] elements) {
    for (PsiElement element : elements) {
      if (element instanceof PyAstClass || element instanceof PyAstFunction) continue;
      if (element instanceof PyAstTargetExpression targetExpression && PyNames.DOCFORMAT.equals(targetExpression.getName())) {
        return targetExpression;
      }
      PyAstTargetExpression targetExpression = getDocFormatAttribute(element.getChildren());
      if (targetExpression != null) return targetExpression;
    }
    return null;
  }

  public final DocStringFormat getFormat() {
    return getState().getFormat();
  }

  public final void setFormat(DocStringFormat format) {
    ServiceState state = getState();
    state.myDocStringFormat = format;
    if (myModule != null) {
      loadState(state);
    }
  }


  public final boolean isAnalyzeDoctest() {
    return getState().myAnalyzeDoctest;
  }

  public final void setAnalyzeDoctest(boolean analyze) {
    ServiceState state = getState();
    state.myAnalyzeDoctest = analyze;
    if (myModule != null) {
      loadState(state);
    }
  }

  public final boolean isInspectDocstring() {
    return getState().myInspectDocstring;
  }

  public final void setInspectDocstring(boolean inspect) {
    ServiceState state = getState();
    state.myInspectDocstring = inspect;
    if (myModule != null) {
      loadState(state);
    }
  }

  public final boolean isRenderExternalDocumentation() {
    return getState().myRenderExternalDocumentation;
  }

  public final void setRenderExternalDocumentation(boolean renderExternalDocumentation) {
    ServiceState state = getState();
    state.myRenderExternalDocumentation = renderExternalDocumentation;
    if (myModule != null) {
      loadState(state);
    }
  }


  public static final class ServiceState {
    private DocStringFormat myDocStringFormat;
    @OptionTag("analyzeDoctest")
    public boolean myAnalyzeDoctest = true;
    @OptionTag("renderExternalDocumentation")
    public boolean myRenderExternalDocumentation;
    @OptionTag("inspectDocstrings")
    public boolean myInspectDocstring;

    public ServiceState(DocStringFormat docStringFormat) {
      myDocStringFormat = docStringFormat;
    }

    ServiceState() {
      this(DEFAULT_DOC_STRING_FORMAT);
    }

    public DocStringFormat getFormat() {
      return myDocStringFormat;
    }

    public void setFormat(@Nullable DocStringFormat format) {
      if (format == null) {
        myDocStringFormat = DocStringFormat.PLAIN;
      } else {
        myDocStringFormat = format;
      }
    }
  }
}
