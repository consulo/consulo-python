// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.facet;

import consulo.content.bundle.Sdk;
import org.jspecify.annotations.Nullable;


public class PythonFacetSettings {
    protected @Nullable Sdk mySdk;

    public @Nullable Sdk getSdk() {
        return mySdk;
    }

    public void setSdk(@Nullable Sdk sdk) {
        mySdk = sdk;
    }
}
