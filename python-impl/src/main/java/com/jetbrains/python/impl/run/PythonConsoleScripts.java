// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.run;

import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import consulo.process.cmd.ParametersList;
import consulo.util.lang.StringUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Top-level functions of JetBrains' PythonConsoleScripts.kt ({@code @file:JvmName("PythonConsoleScripts")}).
 * <p>
 * Only the {@code String} (non-target) variant is ported: {@code buildScriptFunctionWithConsoleRun} and the
 * {@code TargetEnvironmentFunction} script builder belong to the Target API.
 */
public final class PythonConsoleScripts {
    private PythonConsoleScripts() {
    }

    /**
     * Composes lines for execution in Python REPL to run Python script specified in the given {@code config}.
     * <p>
     * Uses {@code runfile()} method defined in {@code _pydev_bundle/pydev_umd.py}.
     *
     * @param config Python run configuration with the information about Python script, its arguments, environment variables and the working
     *               directory
     * @return lines to be executed in Python REPL
     */
    public static String buildScriptWithConsoleRun(PythonRunConfiguration config) {
        // TODO consulo: getPathMapper (PyRemotePathMapper of a remote SDK) missing, paths are passed as they are
        StringBuilder scriptBuilder = new StringBuilder();
        Map<String, String> configEnvs = config.getEnvs();
        configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED);
        if (!configEnvs.isEmpty()) {
            scriptBuilder.append("import os\n");
            for (Map.Entry<String, String> entry : configEnvs.entrySet()) {
                scriptBuilder.append("os.environ[" + toStringLiteral(entry.getKey()) + "] = " + toStringLiteral(entry.getValue()) + "\n");
            }
        }
        String scriptPath = PythonScriptCommandLineState.getExpandedScriptName(config);
        String workingDir = config.getWorkingDirectory();
        scriptBuilder.append("runfile(");
        scriptBuilder.append(toStringLiteral(scriptPath));
        // TODO consulo: ProgramParametersConfigurator.expandMacrosAndParseParameters missing, the parameters are parsed without macros
        String[] scriptParameters = ParametersList.parse(StringUtil.notNullize(config.getScriptParameters()));
        if (scriptParameters.length != 0) {
            scriptBuilder.append(", args=["
                + Arrays.stream(scriptParameters).map(PythonConsoleScripts::toStringLiteral).collect(Collectors.joining(", "))
                + "]");
        }
        if (!StringUtil.isEmpty(workingDir)) {
            scriptBuilder.append(", wdir=");
            scriptBuilder.append(toStringLiteral(PythonScriptCommandLineState.getExpandedWorkingDir(config)));
        }
        // Consulo: PythonRunConfiguration has no module mode, so ", is_module=True" is never added
        scriptBuilder.append(")");
        return scriptBuilder.toString();
    }

    private static String escape(String s) {
        return StringUtil.escapeCharCharacters(s);
    }

    /**
     * Returns this {@link String} as Python string literal.
     * <p>
     * Whitespaces, backslashes and single quotes are escaped and the escaped string is wrapped in single quotes.
     */
    public static String toStringLiteral(String s) {
        return "'" + escape(s) + "'";
    }
}
