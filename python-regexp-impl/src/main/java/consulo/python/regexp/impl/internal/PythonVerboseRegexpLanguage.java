// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.python.regexp.impl.internal;

import com.jetbrains.python.PyNames;
import consulo.language.Language;
import org.intellij.lang.regexp.RegExpLanguage;

public class PythonVerboseRegexpLanguage extends Language {
    public static final PythonVerboseRegexpLanguage INSTANCE = new PythonVerboseRegexpLanguage();

    private PythonVerboseRegexpLanguage() {
        super(RegExpLanguage.INSTANCE, PyNames.VERBOSE_REG_EXP_LANGUAGE_ID);
    }
}
