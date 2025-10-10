/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.impl.inspections;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nonnull;

/**
 * Dummy inspection for configuring the PEP8 checker. The checking itself is performed by
 * Pep8ExternalAnnotator.
 *
 * @author yole
 */
@ExtensionImpl
public class PyPep8Inspection extends PyInspection {
    public static final String INSPECTION_SHORT_NAME = "PyPep8Inspection";
    public static final Key<PyPep8Inspection> KEY = Key.create(INSPECTION_SHORT_NAME);

    @Nonnull
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyPep8InspectionState();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("PEP 8 coding style violation");
    }
}
