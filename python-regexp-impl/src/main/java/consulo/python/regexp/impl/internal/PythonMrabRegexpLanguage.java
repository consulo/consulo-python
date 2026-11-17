// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.language.Language;
import org.intellij.lang.regexp.RegExpLanguage;

/**
 * The dialect of the third-party {@code regex} module by Matthew Barnett (mrab-regex).
 */
final class PythonMrabRegexpLanguage extends Language {
    public static final PythonMrabRegexpLanguage INSTANCE = new PythonMrabRegexpLanguage();

    private PythonMrabRegexpLanguage() {
        super(RegExpLanguage.INSTANCE, "PythonMrabRegExp");
    }
}
