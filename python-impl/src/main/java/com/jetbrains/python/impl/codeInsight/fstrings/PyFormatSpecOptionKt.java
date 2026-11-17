// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.fstrings;

import consulo.util.dataholder.Key;

public final class PyFormatSpecOptionKt {
    private PyFormatSpecOptionKt() {
    }

    /**
     * Key that carries the option a format-spec lookup element stands for, so the documentation provider can
     * describe the element without parsing its text again.
     */
    public static final Key<PyFormatSpecOption> FORMAT_SPEC_OPTION_KEY = Key.create("py.fstring.format.spec.option");
}
