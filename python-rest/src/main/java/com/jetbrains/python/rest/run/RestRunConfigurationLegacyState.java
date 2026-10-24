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

import org.jspecify.annotations.Nullable;

/**
 * The {@code <option name value>} fields a reStructuredText configuration wrote before the fragment model.
 */
public class RestRunConfigurationLegacyState {
    public @Nullable String docutils_input_file;
    public @Nullable String docutils_output_file;
    public @Nullable String docutils_params;
    public @Nullable String docutils_task;
    public boolean docutils_open_in_browser;
}
