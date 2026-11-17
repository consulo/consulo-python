// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

/**
 * The order of the children of a container in the Variables view.
 * <p>
 * {@link #DO_NOT_SORT} keeps the order that the debugger sends. For a dict, a list, a tuple and a set this is the
 * order of the object itself. {@link #BY_TYPE} puts the children of one type together.
 */
public enum SortingPolicy implements AbstractPolicy {
    DO_NOT_SORT,
    ALPHABETICALLY,
    BY_TYPE
}
