// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.sphinx;

import java.util.List;

/**
 * User : catherine
 */
public final class SphinxTasksModel {
    public static final String DEFAULT_TASK = "html";

    private static final List<String> TASKS = List.of(
        "changes",
        "coverage",
        "devhelp",
        "dirhtml",
        "doctest",
        "epub",
        "gettext",
        "html",
        "htmlhelp",
        "json",
        "latex",
        "latexpdf",
        "linkcheck",
        "man",
        "pickle",
        "qthelp",
        "singlehtml",
        "text",
        "texinfo",
        "web",
        "websupport"
    );

    private SphinxTasksModel() {
    }

    public static List<String> getTasks() {
        return TASKS;
    }
}
