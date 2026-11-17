// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

public enum ValuesPolicy implements AbstractPolicy {
    SYNC,
    ASYNC,
    ON_DEMAND
}
