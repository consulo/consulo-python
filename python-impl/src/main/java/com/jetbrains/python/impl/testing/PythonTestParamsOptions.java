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

import org.jspecify.annotations.Nullable;

/**
 * Options of a test configuration that passes extra arguments to its runner.
 */
public interface PythonTestParamsOptions {
    @Nullable String getParams();

    void setParams(@Nullable String params);

    boolean isUseParam();

    void setUseParam(boolean useParam);
}
