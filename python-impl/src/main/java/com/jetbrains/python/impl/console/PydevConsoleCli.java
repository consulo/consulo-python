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
package com.jetbrains.python.impl.console;

import consulo.process.cmd.ParamsGroup;

public final class PydevConsoleCli {
    public static final String MODE_OPTION = "mode";
    public static final String MODE_OPTION_CLIENT_VALUE = "client";

    public static final String HOST_OPTION = "host";
    public static final String PORT_OPTION = "port";

    private PydevConsoleCli() {
    }

    private static String getOptionString(String name, Object value) {
        return "--" + name + "=" + value;
    }

    /**
     * Consulo: JB {@code createPythonConsoleScriptInClientMode()} builds a {@code PythonExecution} of the
     * {@link com.jetbrains.python.impl.PythonHelper#CONSOLE} helper with the Targets API. Consulo keeps
     * {@link consulo.process.cmd.GeneralCommandLine}: the helper is added to the script group by the caller, and this method adds
     * the same client mode options after it.
     *
     * @param ideServerHost the host where the IDE being Python Console frontend listens for the connection
     * @param ideServerPort the port where the IDE being Python Console frontend listens for the connection
     */
    static void addClientModeOptions(ParamsGroup scriptGroup, String ideServerHost, int ideServerPort) {
        scriptGroup.addParameter(getOptionString(MODE_OPTION, MODE_OPTION_CLIENT_VALUE));
        scriptGroup.addParameter(getOptionString(HOST_OPTION, ideServerHost));
        scriptGroup.addParameter(getOptionString(PORT_OPTION, ideServerPort));
    }
}
