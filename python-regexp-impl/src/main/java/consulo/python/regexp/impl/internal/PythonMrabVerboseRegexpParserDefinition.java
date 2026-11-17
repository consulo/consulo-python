// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class PythonMrabVerboseRegexpParserDefinition extends PythonMrabRegexpParserDefinition {
    private static final IFileElementType FILE =
        new IFileElementType("PYTHON_MRAB_VERBOSE_REGEXP_FILE", PythonMrabVerboseRegexpLanguage.INSTANCE);

    @Override
    public Language getLanguage() {
        return PythonMrabVerboseRegexpLanguage.INSTANCE;
    }

    @Override
    public EnumSet<RegExpCapability> getCapabilities() {
        return PythonMrabRegexpKt.VERBOSE_CAPABILITIES;
    }

    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new RegExpFile(viewProvider, PythonMrabVerboseRegexpLanguage.INSTANCE);
    }
}
