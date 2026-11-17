// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest;

import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkUtil;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.ProcessOutput;
import consulo.project.Project;
import consulo.python.rest.localize.PythonRestLocalize;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static com.jetbrains.python.impl.PythonHelper.REST_RUNNER;

public final class RestPythonPreviewProvider {
  private RestPythonPreviewProvider() {
  }

  public static @Nullable Pair<String, String> toHtml(String text, VirtualFile virtualFile, Project project) {
    Module module = Application.get().runReadAction(
      (Supplier<@Nullable Module>)() -> ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
    );
    if (module == null) {
      return null;
    }
    // TODO consulo: findModuleSdkIfReady missing, PythonSdkUtil.findPythonSdk used instead
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) {
      // TODO consulo: PyBundle key python.sdk.no.interpreter.configured.warning missing in PyLocalize, PythonRestLocalize used instead
      return Pair.create("", PythonRestLocalize.pythonSdkNoInterpreterConfiguredWarning().get());
    }
    GeneralCommandLine commandLine = REST_RUNNER.newCommandLine(sdk, List.of("rst2html"));
    ProcessOutput output = PySdkUtil.getProcessOutput(commandLine, virtualFile.getParent().getPath(), null, 5000,
                                                      text.getBytes(StandardCharsets.UTF_8), false);
    if (output.isCancelled() || output.isTimeout()) {
      return Pair.create("", PythonRestLocalize.pythonRestFailedToGenerateHtml().get());
    }
    else {
      return Pair.create(SphinxDirectivesHandler.applySphinx(output.getStdout()),
                         "<h3>" + PythonRestLocalize.pythonRestErrorOutput().get() + "</h3>" + output.getStderr());
    }
  }
}
