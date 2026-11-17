// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements;

import consulo.language.file.LanguageFileType;
import consulo.localize.LocalizeValue;
import consulo.python.requirements.localize.PyRequirementsLocalize;
import consulo.ui.image.Image;

public final class RequirementsFileType extends LanguageFileType {
    public static final RequirementsFileType INSTANCE = new RequirementsFileType();

    private RequirementsFileType() {
        super(RequirementsLanguage.INSTANCE);
    }

    @Override
    public String getId() {
        return "Requirements.txt";
    }

    @Override
    public LocalizeValue getDescription() {
        return PyRequirementsLocalize.filetypeRequirementsTxtDescription();
    }

    @Override
    public String getDefaultExtension() {
        return "txt";
    }

    @Override
    public Image getIcon() {
        return RequirementsIcons.FILE;
    }
}
