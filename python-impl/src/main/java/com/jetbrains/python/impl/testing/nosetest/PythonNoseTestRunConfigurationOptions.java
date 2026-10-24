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
package com.jetbrains.python.impl.testing.nosetest;

import com.dslplatform.json.CompiledJson;
import com.jetbrains.python.impl.testing.PythonTestParamsOptions;
import com.jetbrains.python.impl.testing.PythonTestRunConfigurationOptions;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

@CompiledJson
public class PythonNoseTestRunConfigurationOptions extends PythonTestRunConfigurationOptions implements PythonTestParamsOptions {
    private @Nullable String myParams;
    private boolean myUseParam;

    @Override
    @Attribute("params")
    public @Nullable String getParams() {
        return myParams;
    }

    @Override
    public void setParams(@Nullable String params) {
        myParams = StringUtil.nullize(params, true);
    }

    @Override
    @Attribute("useParam")
    public boolean isUseParam() {
        return myUseParam;
    }

    @Override
    public void setUseParam(boolean useParam) {
        myUseParam = useParam;
    }
}
