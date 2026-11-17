// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.util.collection.SLRUMap;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Caches the output of the external docstring formatter (see {@link PyRuntimeDocstringFormatter}).
 * <p>
 * Rendering Python documentation reformats docstrings by spawning an external interpreter process,
 * which is expensive and is repeated on every documentation render (the documentation popup
 * re-requests docs frequently). The formatter output is effectively a pure function of the input
 * text, the format and the flags for a given SDK, so identical requests can reuse a previous result
 * instead of spawning another process.
 */
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PyDocstringFormatterCache {

    public record Key(
        String sdkHome,
        LanguageLevel languageLevel,
        String formatterCommand,
        List<String> flags,
        String input
    ) {
    }

    private static final int PROTECTED_QUEUE_SIZE = 64;
    private static final int PROBATIONAL_QUEUE_SIZE = 64;

    private final SLRUMap<Key, String> myCache = new SLRUMap<>(PROTECTED_QUEUE_SIZE, PROBATIONAL_QUEUE_SIZE);

    public @Nullable String getOrCompute(Key key, Supplier<@Nullable String> compute) {
        String cached;
        synchronized (myCache) {
            cached = myCache.get(key);
        }
        if (cached != null) {
            return cached;
        }

        String computed = compute.get();
        if (computed == null) {
            return null;
        }

        synchronized (myCache) {
            myCache.put(key, computed);
        }
        return computed;
    }

    public static PyDocstringFormatterCache getInstance() {
        return Application.get().getInstance(PyDocstringFormatterCache.class);
    }
}
