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
package com.jetbrains.python.impl.run;

import com.dslplatform.json.CompiledJson;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * The interpreter settings shared by every Python configuration; a {@code null} SDK home means the module SDK.
 */
@CompiledJson
public class AbstractPythonRunConfigurationOptions extends RunConfigurationOptions {
    private @Nullable String myInterpreterOptions;
    private @Nullable String mySdkHome;
    private boolean myUseModuleSdk;
    private boolean myAddContentRoots = true;
    private boolean myAddSourceRoots = true;

    @Attribute("interpreterOptions")
    public @Nullable String getInterpreterOptions() {
        return myInterpreterOptions;
    }

    public void setInterpreterOptions(@Nullable String interpreterOptions) {
        myInterpreterOptions = StringUtil.nullize(interpreterOptions, true);
    }

    @Attribute("sdkHome")
    public @Nullable String getSdkHome() {
        return mySdkHome;
    }

    public void setSdkHome(@Nullable String sdkHome) {
        mySdkHome = StringUtil.nullize(sdkHome, true);
    }

    @Attribute("useModuleSdk")
    public boolean isUseModuleSdk() {
        return myUseModuleSdk;
    }

    public void setUseModuleSdk(boolean useModuleSdk) {
        myUseModuleSdk = useModuleSdk;
    }

    @Attribute("addContentRoots")
    public boolean isAddContentRoots() {
        return myAddContentRoots;
    }

    public void setAddContentRoots(boolean addContentRoots) {
        myAddContentRoots = addContentRoots;
    }

    @Attribute("addSourceRoots")
    public boolean isAddSourceRoots() {
        return myAddSourceRoots;
    }

    public void setAddSourceRoots(boolean addSourceRoots) {
        myAddSourceRoots = addSourceRoots;
    }
}
