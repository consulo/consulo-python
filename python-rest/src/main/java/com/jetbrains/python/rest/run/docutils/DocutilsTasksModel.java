// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.docutils;

import java.util.List;

/**
 * User : catherine
 */
public final class DocutilsTasksModel {
    public static final String DEFAULT_TASK = "rst2html";

    private static final List<String> TASKS = List.of(
        "rst2html",
        "rst2latex",
        "rst2odt",
        "rst2pseudoxml",
        "rst2s5",
        "rst2xml",
        "rstpep2html"
    );

    private DocutilsTasksModel() {
    }

    public static List<String> getTasks() {
        return TASKS;
    }
}
