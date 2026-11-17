// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiFile;
import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpFile;

import java.util.EnumSet;

@ExtensionImpl
public final class PythonVerboseRegexpParserDefinition extends PythonRegexpParserDefinition {
    public static final IFileElementType VERBOSE_PYTHON_REGEXP_FILE =
        new IFileElementType("VERBOSE_PYTHON_REGEXP_FILE", PythonVerboseRegexpLanguage.INSTANCE);
    private final EnumSet<RegExpCapability> VERBOSE_CAPABILITIES;

    public PythonVerboseRegexpParserDefinition() {
        VERBOSE_CAPABILITIES = EnumSet.copyOf(CAPABILITIES);
        VERBOSE_CAPABILITIES.add(RegExpCapability.COMMENT_MODE);
    }

    @Override
    public Language getLanguage() {
        return PythonVerboseRegexpLanguage.INSTANCE;
    }

    @Override
    public EnumSet<RegExpCapability> getCapabilities() {
        return VERBOSE_CAPABILITIES;
    }

    @Override
    public IFileElementType getFileNodeType() {
        return VERBOSE_PYTHON_REGEXP_FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new RegExpFile(viewProvider, PythonVerboseRegexpLanguage.INSTANCE);
    }
}
