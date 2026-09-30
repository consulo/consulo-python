/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
