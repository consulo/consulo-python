// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unresolvedReference;

import java.util.Map;

public final class PyCommonImportAliasesKt {
    private PyCommonImportAliasesKt() {
    }

    public static final Map<String, String> PY_COMMON_IMPORT_ALIASES = Map.ofEntries(
        Map.entry("np", "numpy"),
        Map.entry("pl", "pylab"),
        Map.entry("p", "pylab"),
        Map.entry("sp", "scipy"),
        Map.entry("pd", "pandas"),
        Map.entry("sym", "sympy"),
        Map.entry("sm", "statmodels"),
        Map.entry("nx", "networkx"),
        Map.entry("sk", "sklearn"),

        Map.entry("plt", "matplotlib.pyplot"),
        Map.entry("mpimg", "matplotlib.image"),
        Map.entry("mimg", "matplotlib.image")
    );
}
