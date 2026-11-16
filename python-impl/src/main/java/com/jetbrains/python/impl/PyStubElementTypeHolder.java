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
package com.jetbrains.python.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.ObjectStubSerializerProvider;
import consulo.language.psi.stub.StubElementTypeHolder;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Registers the stub element types of {@link PyStubElementTypes} (JB: {@code <stubElementTypeHolder class="com.jetbrains.python.PyStubElementTypes"/>}).
 */
@ExtensionImpl
public class PyStubElementTypeHolder extends StubElementTypeHolder<PyStubElementTypes> {
    @Override
    public @Nullable String getExternalIdPrefix() {
        return null;
    }

    @Override
    public List<ObjectStubSerializerProvider> loadSerializers() {
        return allFromStaticFields(PyStubElementTypes.class, Field::get);
    }
}
