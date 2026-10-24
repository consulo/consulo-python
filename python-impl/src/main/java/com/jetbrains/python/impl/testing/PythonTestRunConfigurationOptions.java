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
package com.jetbrains.python.impl.testing;

import com.dslplatform.json.CompiledJson;
import com.jetbrains.python.impl.run.AbstractPythonRunConfigurationOptions;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * The test target of a Python test configuration; a {@code null} test type means {@link AbstractPythonTestRunConfiguration.TestType#TEST_SCRIPT}.
 */
@CompiledJson
public class PythonTestRunConfigurationOptions extends AbstractPythonRunConfigurationOptions {
    private @Nullable String myScriptName;
    private @Nullable String myClassName;
    private @Nullable String myMethodName;
    private @Nullable String myFolderName;
    private @Nullable String myTestType;
    private @Nullable String myPattern;
    private boolean myUsePattern;

    @Attribute("scriptName")
    public @Nullable String getScriptName() {
        return myScriptName;
    }

    public void setScriptName(@Nullable String scriptName) {
        myScriptName = StringUtil.nullize(scriptName, true);
    }

    @Attribute("className")
    public @Nullable String getClassName() {
        return myClassName;
    }

    public void setClassName(@Nullable String className) {
        myClassName = StringUtil.nullize(className, true);
    }

    @Attribute("methodName")
    public @Nullable String getMethodName() {
        return myMethodName;
    }

    public void setMethodName(@Nullable String methodName) {
        myMethodName = StringUtil.nullize(methodName, true);
    }

    @Attribute("folderName")
    public @Nullable String getFolderName() {
        return myFolderName;
    }

    public void setFolderName(@Nullable String folderName) {
        myFolderName = StringUtil.nullize(folderName, true);
    }

    @Attribute("testType")
    public @Nullable String getTestType() {
        return myTestType;
    }

    public void setTestType(@Nullable String testType) {
        myTestType = StringUtil.nullize(testType, true);
    }

    @Attribute("pattern")
    public @Nullable String getPattern() {
        return myPattern;
    }

    public void setPattern(@Nullable String pattern) {
        myPattern = StringUtil.nullize(pattern, true);
    }

    @Attribute("usePattern")
    public boolean isUsePattern() {
        return myUsePattern;
    }

    public void setUsePattern(boolean usePattern) {
        myUsePattern = usePattern;
    }
}
