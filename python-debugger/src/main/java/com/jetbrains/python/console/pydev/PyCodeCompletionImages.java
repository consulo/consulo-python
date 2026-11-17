// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.console.pydev;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

public final class PyCodeCompletionImages {
    /**
     * Returns an image for the given type
     */
    public static @Nullable Image getImageForType(int type) {
        return switch (type) {
            case IToken.TYPE_CLASS -> PlatformIconGroup.nodesClass();
            case IToken.TYPE_FUNCTION -> PlatformIconGroup.nodesMethod();
            default -> null;
        };
    }
}
