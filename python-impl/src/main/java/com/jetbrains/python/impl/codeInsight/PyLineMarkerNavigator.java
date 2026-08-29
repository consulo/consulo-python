/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.util.query.Query;
import consulo.language.editor.gutter.GutterIconNavigationHandler;
import consulo.language.editor.ui.navigation.PsiTargetNavigationService;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.ComponentEvent;
import consulo.util.concurrent.coroutine.step.CodeExecution;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
abstract class PyLineMarkerNavigator<T extends PsiElement> implements GutterIconNavigationHandler<T> {
    /**
     * A popup cannot be inspected from a test, so in unit test mode the targets are left on the element
     * instead of being shown, and read back through {@link #getNavigationTargets(UserDataHolder)}.
     */
    private static final Key<NavigatablePsiElement[]> MARKERS = new Key<>("PyLineMarkerNavigatorMarkers");

    @Override
    @RequiredUIAccess
    public void navigate(ComponentEvent<?> e, T elt) {
        LocalizeValue title = ReadAction.compute(() -> getTitle(elt));

        Application.get().getInstance(PsiTargetNavigationService.class)
            .<NavigatablePsiElement>newNavigator(CodeExecution.supply(() -> {
                Collection<NavigatablePsiElement> targets = collect(elt);
                return targets == null ? List.of() : targets;
            }))
            .title(title)
            .navigate(e, elt.getProject());
    }

    private @Nullable List<NavigatablePsiElement> collect(T elt) {
        Query<T> query = ReadAction.compute(
            () -> search(elt, TypeEvalContext.userInitiated(elt.getProject(), elt.getContainingFile()))
        );
        if (query == null) {
            return null;
        }

        List<NavigatablePsiElement> targets = new ArrayList<>();
        query.forEach(element -> {
            if (element instanceof NavigatablePsiElement navigatable) {
                targets.add(navigatable);
            }
            return true;
        });
        return targets;
    }

    @TestOnly
    static @Nullable NavigatablePsiElement[] getNavigationTargets(UserDataHolder holder) {
        return holder.getUserData(MARKERS);
    }

    @RequiredReadAction
    protected abstract LocalizeValue getTitle(T elt);

    @RequiredReadAction
    protected abstract @Nullable Query<T> search(T elt, TypeEvalContext context);
}
