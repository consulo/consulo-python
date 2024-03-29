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

package com.jetbrains.python.rest;

import com.jetbrains.rest.RestLanguage;
import com.jetbrains.rest.RestTokenTypes;
import consulo.language.impl.psi.template.TemplateDataElementType;

/**
 * User : catherine
 */
public interface RestPythonElementTypes {
  TemplateDataElementType PYTHON_BLOCK_DATA = new RestPythonTemplateType("PYTHON_BLOCK_DATA", RestLanguage.INSTANCE,
                                                                         RestTokenTypes.PYTHON_LINE, RestTokenTypes.REST_INJECTION);

  TemplateDataElementType DJANGO_BLOCK_DATA = new RestPythonTemplateType("DJANGO_BLOCK_DATA", RestLanguage.INSTANCE,
                                RestTokenTypes.DJANGO_LINE, RestTokenTypes.REST_DJANGO_INJECTION);
}

