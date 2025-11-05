package com.jetbrains.python.console.pydev;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

import jakarta.annotation.Nullable;

public class PyCodeCompletionImages {
    /**
     * Returns an image for the given type
     */
    @Nullable
    public static Image getImageForType(int type) {
        return switch (type) {
            case IToken.TYPE_CLASS -> PlatformIconGroup.nodesClass();
            case IToken.TYPE_FUNCTION -> PlatformIconGroup.nodesMethod();
            default -> null;
        };
    }
}
