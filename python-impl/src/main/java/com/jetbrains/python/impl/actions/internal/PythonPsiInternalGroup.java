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
package com.jetbrains.python.impl.actions.internal;

import consulo.python.impl.localize.PyLocalize;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * Consulo registration of JB's {@code PythonPsiInternalGroup} action group (intellij.python.psi.impl.xml).
 */
@ActionImpl(
    id = "PythonPsiInternalGroup",
    children = @ActionRef(type = PyTypeBenchmarkAction.class),
    parents = @ActionParentRef(@ActionRef(id = "Internal"))
)
public class PythonPsiInternalGroup extends DefaultActionGroup {
    public PythonPsiInternalGroup() {
        super(PyLocalize.groupPythonpsiinternalgroupText(), true);
    }
}
