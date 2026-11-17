// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements;

import consulo.language.Language;

public final class RequirementsLanguage extends Language {
    public static final RequirementsLanguage INSTANCE = new RequirementsLanguage();

    private RequirementsLanguage() {
        super("Requirements");
    }
}
