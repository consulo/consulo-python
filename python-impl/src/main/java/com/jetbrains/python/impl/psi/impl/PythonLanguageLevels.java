// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.impl.psi.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The language levels of a project: a {@link PythonLanguageLevelLayout} for each snapshot of {@code EvoPyProjectModel}, and
 * for each change that keeps the interpreters of a snapshot, such as a new version or new class roots, see
 * {@code PythonInterpreterRoots.changes}.
 * <p>
 * {@link PythonLanguageLevelPusher} reads {@link #getLayout()} for each file. A new layout becomes {@link #getLayout()} in the
 * same step that pushes it to the roots, so a file never reads a level that the roots do not have yet, see {@link #start()}.
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PythonLanguageLevels {
    private final Project project;

    private final AtomicReference<PythonLanguageLevelLayout> layoutState = new AtomicReference<>(PythonLanguageLevelLayout.EMPTY);

    private final AtomicBoolean started = new AtomicBoolean();

    @Inject
    public PythonLanguageLevels(Project project) {
        this.project = project;
    }

    /**
     * The layout that the pusher pushed last. {@link PythonLanguageLevelLayout#EMPTY} before the first push.
     */
    public PythonLanguageLevelLayout getLayout() {
        return layoutState.get();
    }

    /**
     * Pushes the layout of the current snapshot, and then each new layout. The pusher calls it once, before the first scan
     * of the project, so the scan reads the levels of the snapshot. Later layouts become {@link #getLayout()} on the EDT with
     * their push.
     * <p>
     * The service does not start itself. A reader of a level can create it first, in a read action, and a push there queues
     * a dumb task, which takes a write action. Until this call, a reader gets {@link PythonLanguageLevelLayout#EMPTY}, and the
     * first layout has one writer: this call computes it before the collector starts.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        // No collector runs yet, so this thread is the only writer.
        PythonLanguageLevelLayout first = PythonLanguageLevelLayout.of(project);
        PythonLanguageLevelPusher.push(project, first, layoutState.getAndSet(first));
        // TODO consulo: EvoPyProjectModel.snapshotFlow() and PythonInterpreterRoots.changes missing - there is no collector
        //  of new layouts, the pusher takes a new layout on each roots change instead, see updateLayout()
    }

    /**
     * Consulo: stands in for the collector of {@link #start()}, which Consulo cannot run without the snapshot flow. Makes the
     * layout of the current modules and SDKs {@link #getLayout()}, and returns the layout before it.
     */
    PythonLanguageLevelLayout updateLayout() {
        PythonLanguageLevelLayout layout = PythonLanguageLevelLayout.of(project);
        return layoutState.getAndSet(layout);
    }

    // TODO consulo: awaitPushedForTests (suspend, @TestOnly) not ported - EvoPyProjectModel and coroutines missing

    public static PythonLanguageLevels getInstance(Project project) {
        return project.getInstance(PythonLanguageLevels.class);
    }

    /**
     * The current layout of {@code project}.
     */
    public static PythonLanguageLevelLayout layoutOf(Project project) {
        return getInstance(project).getLayout();
    }
}
