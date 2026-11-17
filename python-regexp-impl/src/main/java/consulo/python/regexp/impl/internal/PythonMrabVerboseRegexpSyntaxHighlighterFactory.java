// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import org.intellij.lang.regexp.RegExpSyntaxHighlighterFactory;

@ExtensionImpl
public class PythonMrabVerboseRegexpSyntaxHighlighterFactory extends RegExpSyntaxHighlighterFactory {
    public PythonMrabVerboseRegexpSyntaxHighlighterFactory() {
        super(PythonMrabVerboseRegexpLanguage.INSTANCE);
    }

    @Override
    public Language getLanguage() {
        return PythonMrabVerboseRegexpLanguage.INSTANCE;
    }
}
