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
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;

/**
 * Consulo registration of {@link PyBuiltinHighlightingAnnotator} (JB: {@code <annotator language="Python" .../>}).
 */
@ExtensionImpl
public class PyBuiltinHighlightingAnnotatorFactory implements AnnotatorFactory {
    @Override
    public Annotator createAnnotator() {
        return new PyBuiltinHighlightingAnnotator();
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
