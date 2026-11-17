// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.language.Language;
import org.intellij.lang.regexp.RegExpLanguage;

/**
 * The {@link PythonMrabRegexpLanguage} dialect with the {@code VERBOSE} flag.
 */
final class PythonMrabVerboseRegexpLanguage extends Language {
    public static final PythonMrabVerboseRegexpLanguage INSTANCE = new PythonMrabVerboseRegexpLanguage();

    private PythonMrabVerboseRegexpLanguage() {
        super(RegExpLanguage.INSTANCE, "PythonMrabVerboseRegExp");
    }
}
