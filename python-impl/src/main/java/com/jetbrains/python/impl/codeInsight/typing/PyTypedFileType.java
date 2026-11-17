// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import consulo.application.AllIcons;
import consulo.language.file.LanguageFileType;
import consulo.language.plain.PlainTextLanguage;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.image.Image;

public final class PyTypedFileType extends LanguageFileType {
    public static final PyTypedFileType INSTANCE = new PyTypedFileType();

    private PyTypedFileType() {
        super(PlainTextLanguage.INSTANCE, true);
    }

    @Override
    public String getId() {
        return "PyTyped";
    }

    @Override
    public LocalizeValue getDescription() {
        return PyCodeInsightLocalize.filetypePyTypedDescription();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyCodeInsightLocalize.filetypePyTypedDisplayName();
    }

    @Override
    public String getDefaultExtension() {
        return "typed";
    }

    @Override
    public Image getIcon() {
        return AllIcons.FileTypes.Text;
    }
}
