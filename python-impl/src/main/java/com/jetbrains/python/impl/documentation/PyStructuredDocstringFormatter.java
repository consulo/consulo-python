// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import consulo.application.ApplicationManager;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.ApplicationUtil;
import consulo.component.ProcessCanceledException;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.module.Module;
import org.jspecify.annotations.Nullable;

import java.util.List;


public final class PyStructuredDocstringFormatter {

  private static final Logger LOG = Logger.getInstance(PyStructuredDocstringFormatter.class);
  static final String FORMATTER_FRAGMENTS_FLAG = "--fragments";

  private PyStructuredDocstringFormatter() {
  }

  public static PyDocumentationBuilder.@Nullable DocstringFormatterRequest formatDocstring(PsiElement element,
                                                                                           PyDocumentationBuilder.DocstringFormatterRequest docstringFormatterRequest,
                                                                                           List<String> flags) {
    Module module = DocStringParser.getModuleForElement(element);
    if (module == null) return new PyDocumentationBuilder.DocstringFormatterRequest();

    final String docstring = docstringFormatterRequest.getBody();
    final String preparedDocstring = PyIndentUtil.removeCommonIndent(docstring, true).trim();
    final DocStringFormat format = DocStringParser.guessDocStringFormat(preparedDocstring, element);

    if (format == DocStringFormat.PLAIN) {
      return null;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new PyDocumentationBuilder.DocstringFormatterRequest("Unittest placeholder", docstringFormatterRequest.getFragments());
    }

    final PyDocumentationBuilder.DocstringFormatterRequest inputStructure =
      new PyDocumentationBuilder.DocstringFormatterRequest(preparedDocstring, docstringFormatterRequest.getFragments());

    try {
      final String outputStr = runDocstringFormatterService(flags, module, format, new Gson().toJson(inputStructure));
      return new Gson().fromJson(outputStr, PyDocumentationBuilder.DocstringFormatterRequest.class);
    }
    catch (JsonSyntaxException e) {
      return docstringFormatterRequest;
    }
  }

  private static String runDocstringFormatterService(List<String> flags,
                                                     Module module,
                                                     DocStringFormat format,
                                                     String docstring) {
    String result = null;
    try {
      result = ApplicationUtil.runWithCheckCanceled(
        () -> PythonRuntimeService.getInstance().formatDocstring(module, format, docstring, flags),
        // It's supposed to be run inside a non-blocking read action and, thus, have an associated progress indicator
        EmptyProgressIndicator.notNullize(ProgressManager.getInstance().getProgressIndicator())
      );
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.warn(e);
    }

    if (result != null) {
      return result;
    }
    else if (!flags.contains(FORMATTER_FRAGMENTS_FLAG)) {
      return DocStringUtil.parseDocStringContent(format, docstring).getDescription();
    }
    return "";
  }
}
