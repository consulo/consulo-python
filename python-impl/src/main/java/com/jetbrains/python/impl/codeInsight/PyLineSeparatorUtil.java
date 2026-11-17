// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import consulo.application.ReadAction;
import consulo.colorScheme.EditorColorsManager;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

/**
 * @author oleg
 */
public final class PyLineSeparatorUtil {

    private PyLineSeparatorUtil() {
    }

    public interface Provider {
        boolean isSeparatorAllowed(@Nullable PsiElement element);
    }

    public static @Nullable LineMarkerInfo addLineSeparatorIfNeeded(Provider provider, PsiElement element) {
        Ref<LineMarkerInfo> info = new Ref<>(null);
        ReadAction.run(() -> {
            if (!provider.isSeparatorAllowed(element)) {
                return;
            }
            boolean hasSeparableBefore = false;
            PsiElement parent = element.getParent();
            if (parent == null) {
                return;
            }
            for (PsiElement child : parent.getChildren()) {
                if (child == element) {
                    break;
                }
                if (provider.isSeparatorAllowed(child)) {
                    hasSeparableBefore = true;
                    break;
                }
            }
            if (!hasSeparableBefore) {
                return;
            }
            info.set(createLineSeparatorByElement(element));
        });
        return info.get();
    }

    private static LineMarkerInfo<PsiElement> createLineSeparatorByElement(PsiElement element) {
        PsiElement anchor = PsiTreeUtil.getDeepestFirst(element);
        return LineMarkerInfo.createMethodSeparatorLineMarker(anchor, EditorColorsManager.getInstance());
    }
}
