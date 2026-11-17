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

import org.jspecify.annotations.NullMarked;

/**
 * Requirements.txt language support (JetBrains python-requirements module).
 */
@NullMarked
module consulo.python.requirements {
    requires consulo.application.api;
    requires consulo.base.icon.library;
    requires consulo.code.editor.api;
    requires consulo.color.scheme.api;
    requires consulo.component.api;
    requires consulo.language.api;
    requires consulo.language.editor.api;
    requires consulo.language.impl;
    requires consulo.localize.api;
    requires consulo.project.api;
    requires consulo.repository.ui.api;
    requires consulo.ui.api;
    requires consulo.util.io;
    requires consulo.util.lang;
    requires consulo.virtual.file.system.api;

    requires consulo.python.language.api;

    requires one.util.streamex;

    exports com.intellij.python.requirements;
    exports com.intellij.python.requirements.parser;
    exports com.intellij.python.requirements.parser.psi;
    exports com.intellij.python.requirements.parser.psi.impl;
    exports com.jetbrains.python.requirements;
    exports com.jetbrains.python.requirements.impl;
    exports consulo.python.requirements.icon;
    exports consulo.python.requirements.localize;
}
