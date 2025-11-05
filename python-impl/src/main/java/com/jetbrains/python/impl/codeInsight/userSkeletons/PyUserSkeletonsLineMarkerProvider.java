/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.userSkeletons;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.language.Language;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiNavigateUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
@ExtensionImpl
public class PyUserSkeletonsLineMarkerProvider implements LineMarkerProvider {
    @Nullable
    @Override
    @RequiredReadAction
    public LineMarkerInfo getLineMarkerInfo(@Nonnull PsiElement element) {
        return null;
    }

    @Override
    @RequiredReadAction
    public void collectSlowLineMarkers(@Nonnull List<PsiElement> elements, @Nonnull Collection<LineMarkerInfo> result) {
        for (PsiElement element : elements) {
            PyElement skeleton = getUserSkeleton(element);
            if (skeleton != null) {
                result.add(new LineMarkerInfo<>(
                    element,
                    element.getTextRange(),
                    PlatformIconGroup.gutterUnique(),
                    Pass.LINE_MARKERS,
                    e -> "Has user skeleton",
                    (e, elt) -> {
                        PyElement s = getUserSkeleton(elt);
                        if (s != null) {
                            PsiNavigateUtil.navigate(s);
                        }
                    },
                    GutterIconRenderer.Alignment.RIGHT
                ));
            }
        }
    }

    @Nullable
    private static PyElement getUserSkeleton(@Nonnull PsiElement element) {
        if (element instanceof PyFunction || element instanceof PyTargetExpression) {
            return PyUserSkeletonsUtil.getUserSkeleton((PyElement) element);
        }
        return null;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
