// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import consulo.language.DependentLanguage;
import consulo.language.InjectableLanguage;
import consulo.language.Language;
import consulo.language.editor.impl.intention.QuickEditAction;

/**
 * User : ktisha
 */
public class PyDoctestLanguageDialect extends Language implements DependentLanguage, InjectableLanguage {

    public static PyDoctestLanguageDialect getInstance() {
        return (PyDoctestLanguageDialect) PyDoctestFileType.INSTANCE.getLanguage();
    }

    protected PyDoctestLanguageDialect() {
        super(PythonLanguage.getInstance(), PyNames.PY_DOCSTRING_ID);
        putUserData(QuickEditAction.EDIT_ACTION_AVAILABLE, false);
    }
}
