// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

