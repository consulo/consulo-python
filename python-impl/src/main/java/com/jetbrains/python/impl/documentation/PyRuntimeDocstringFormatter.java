// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.google.gson.Gson;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.application.util.HtmlChunk;
import consulo.content.bundle.Sdk;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.ProcessOutput;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.util.ColorUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Consulo: JB runs the helper through the exec-service API ({@code ExecService.executeHelper}) with a {@code PythonInterpreter};
 * here the helper is started with {@link PythonHelper#DOCSTRING_FORMATTER} on the {@link Sdk} and {@link PySdkUtil#getProcessOutput}.
 */
public final class PyRuntimeDocstringFormatter {
    private static final Logger LOG = Logger.getInstance(PyRuntimeDocstringFormatter.class);
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    // Consulo: the exec-service has no timeout parameter here, PySdkUtil needs one
    private static final int TIMEOUT = 5000;

    private PyRuntimeDocstringFormatter() {
    }

    public sealed interface ModuleOrSdk {
        record TheSdk(Sdk sdk) implements ModuleOrSdk {
        }

        record TheModule(Module module) implements ModuleOrSdk {
        }
    }

    public static @Nullable String runExternalTool(ModuleOrSdk moduleOrSdk,
                                                   DocStringFormat format,
                                                   String input,
                                                   List<String> formatterFlags) {
        Sdk sdk;
        if (moduleOrSdk instanceof ModuleOrSdk.TheSdk theSdk) {
            sdk = theSdk.sdk();
        }
        else {
            Sdk localCPython = PythonSdkType.findLocalCPython(((ModuleOrSdk.TheModule) moduleOrSdk).module());
            if (localCPython == null) {
                return logSdkNotFound(format);
            }
            sdk = localCPython;
        }
        String sdkHome = sdk.getHomePath();
        if (sdkHome == null) {
            LOG.debug("Sdk " + sdk + " is broken");
            return null;
        }
        LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        if (languageLevel.isPython2()) {
            return logPy2NotSupported();
        }
        else {
            return formatCached(sdkHome, languageLevel, format, formatterFlags, input, PyDocstringFormatterCache.getInstance(),
                () -> runProcess(sdk, sdkHome, format, formatterFlags, input));
        }
    }

    private static @Nullable String runProcess(Sdk sdk, String sdkHome, DocStringFormat format, List<String> formatterFlags, String input) {
        ByteBuffer encodedInput = DEFAULT_CHARSET.encode(input);
        byte[] data = new byte[encodedInput.limit()];
        encodedInput.get(data);
        List<String> arguments = new ArrayList<>(formatterFlags);
        arguments.add("--format");
        arguments.add(format.getFormatterCommand());

        GeneralCommandLine commandLine = PythonHelper.DOCSTRING_FORMATTER.newCommandLine(sdk, arguments);
        commandLine.setCharset(DEFAULT_CHARSET);

        ProcessOutput result = PySdkUtil.getProcessOutput(commandLine, new File(sdkHome).getParent(), null, TIMEOUT, data, false);

        if (result.isTimeout() || result.isCancelled() || result.getExitCode() != 0) {
            LOG.warn("Error " + result.getStderr() + " for input:\n" + input);
            return null;
        }
        return result.getStdout();
    }

    public static @Nullable String formatCached(String sdkHome,
                                                LanguageLevel languageLevel,
                                                DocStringFormat format,
                                                List<String> formatterFlags,
                                                String input,
                                                PyDocstringFormatterCache cache,
                                                Supplier<@Nullable String> compute) {
        PyDocstringFormatterCache.Key key =
            new PyDocstringFormatterCache.Key(sdkHome, languageLevel, format.getFormatterCommand(), formatterFlags, input);
        return cache.getOrCompute(key, compute);
    }

    private static String logErrorToJsonBody(String message) {
        return new Gson().toJson(
            new PyDocumentationBuilder.DocstringFormatterRequest(
                HtmlChunk.p().attr("color", ColorUtil.toHtmlColor(JBColor.RED)).addRaw(message).toString()));
    }

    private static String logPy2NotSupported() {
        String message = PyLocalize.qdocPython3SdkNeededToRenderDocstrings().get();
        LOG.warn(message);
        return logErrorToJsonBody(message);
    }

    private static String logSdkNotFound(DocStringFormat format) {
        LOG.warn("Python SDK for input formatter " + format + " is not found");
        return logErrorToJsonBody(PyLocalize.qdocPython3SdkNeededToRenderDocstrings().get());
    }
}
