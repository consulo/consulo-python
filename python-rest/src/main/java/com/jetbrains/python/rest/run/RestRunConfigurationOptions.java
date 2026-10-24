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
package com.jetbrains.python.rest.run;

import com.dslplatform.json.CompiledJson;
import com.jetbrains.python.impl.run.AbstractPythonRunConfigurationOptions;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

@CompiledJson
public class RestRunConfigurationOptions extends AbstractPythonRunConfigurationOptions {
    private @Nullable String myInputFile;
    private @Nullable String myOutputFile;
    private @Nullable String myParams;
    private @Nullable String myTask;
    private boolean myOpenInBrowser;

    @Attribute("inputFile")
    public @Nullable String getInputFile() {
        return myInputFile;
    }

    public void setInputFile(@Nullable String inputFile) {
        myInputFile = StringUtil.nullize(inputFile, true);
    }

    @Attribute("outputFile")
    public @Nullable String getOutputFile() {
        return myOutputFile;
    }

    public void setOutputFile(@Nullable String outputFile) {
        myOutputFile = StringUtil.nullize(outputFile, true);
    }

    @Attribute("params")
    public @Nullable String getParams() {
        return myParams;
    }

    public void setParams(@Nullable String params) {
        myParams = StringUtil.nullize(params, true);
    }

    @Attribute("task")
    public @Nullable String getTask() {
        return myTask;
    }

    public void setTask(@Nullable String task) {
        myTask = StringUtil.nullize(task, true);
    }

    @Attribute("openInBrowser")
    public boolean isOpenInBrowser() {
        return myOpenInBrowser;
    }

    public void setOpenInBrowser(boolean openInBrowser) {
        myOpenInBrowser = openInBrowser;
    }
}
