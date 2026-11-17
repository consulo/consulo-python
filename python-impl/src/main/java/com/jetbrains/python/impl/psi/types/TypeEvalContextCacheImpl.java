// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters;
import com.jetbrains.python.psi.types.PyLibraryModificationTracker;
import com.jetbrains.python.psi.types.PyTypeEngineSettingsModificationTracker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.psi.types.TypeEvalContextCache;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.LowMemoryWatcher;
import consulo.component.util.SimpleModificationTracker;
import consulo.language.psi.PsiModificationTracker;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Caches context by their constraints (to prevent context cache loss). Class is thread safe.
 * <p>
 * Contexts for user code are flushed on every PSI change or low memory conditions. Contexts for library code are kept in a
 * separate map that deliberately outlives PSI changes — that is the point of the split — and is flushed only by
 * {@link PyLibraryModificationTracker} (roots change, dumb mode, forced reparse, or an edit inside a library file), low
 * memory conditions, or a change to the type engine settings.
 * See {@link #getContext(TypeEvalContext)}
 *
 * @author Ilya.Kazakevich
 */
@ServiceImpl
@Singleton
public final class TypeEvalContextCacheImpl implements TypeEvalContextCache {
    private final CachedValue<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> myCachedMapStorage;
    private final CachedValue<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> myLibrariesCachedMapStorage;
    private final LowMemoryWatcher myLowMemoryWatcher;
    private final SimpleModificationTracker myLowMemoryModificationTracker = new SimpleModificationTracker();

    @Inject
    TypeEvalContextCacheImpl(Project project) {
        myCachedMapStorage = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<>() {
            @Override
            public CachedValueProvider.Result<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> compute() {
                // This method is called if cache is empty. Create new map for it.
                // Concurrent map allows several threads to call get and put, so it is thread safe but not atomic
                ConcurrentMap<TypeEvalConstraints, TypeEvalContext> map = ContainerUtil.createConcurrentSoftValueMap();
                return new CachedValueProvider.Result<>(map, PsiModificationTracker.MODIFICATION_COUNT, myLowMemoryModificationTracker,
                    PyTypeEngineSettingsModificationTracker.getInstance(project));
            }
        });

        myLibrariesCachedMapStorage = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<>() {
            @Override
            public CachedValueProvider.Result<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> compute() {
                // This method is called if cache is empty. Create new map for it.
                // Concurrent map allows several threads to call get and put, so it is thread safe but not atomic
                ConcurrentMap<TypeEvalConstraints, TypeEvalContext> map = ContainerUtil.createConcurrentSoftValueMap();
                // library types come from the same engine, and nothing else invalidates them when its settings change
                return new CachedValueProvider.Result<>(map, PyLibraryModificationTracker.getInstance(project),
                    myLowMemoryModificationTracker,
                    PyTypeEngineSettingsModificationTracker.getInstance(project));
            }
        });

        myLowMemoryWatcher = LowMemoryWatcher.register(() -> {
            myLowMemoryModificationTracker.incModificationCount();
            myCachedMapStorage.getValue();
        });
    }

    private static TypeEvalContext retrieveFromStorage(TypeEvalContext standard,
                                                       CachedValue<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> storage) {
        // map is thread safe but not atomic nor getValue() is, so in worst case several threads may produce same result
        // both explicit locking and computeIfAbsent leads to deadlock
        TypeEvalConstraints key = ((TypeEvalContextImpl) standard).getConstraints();
        ConcurrentMap<TypeEvalConstraints, TypeEvalContext> map = storage.getValue();
        TypeEvalContext cachedContext = map.get(key);
        if (cachedContext != null) {
            return cachedContext;
        }
        PyCodeInsightCounters.inc(PyCodeInsightCounters.Counter.CONTEXT_LOOKUP_MISSES);
        TypeEvalContext oldValue =
            map.putIfAbsent(key, standard);// ConcurrentMap guarantees happens-before so from this moment get() should work in other threads
        return oldValue == null ? standard : oldValue;
    }

    @Override
    public TypeEvalContext getContext(TypeEvalContext standard) {
        return retrieveFromStorage(standard, myCachedMapStorage);
    }

    @Override
    public TypeEvalContext getLibraryContext(TypeEvalContext standard) {
        return retrieveFromStorage(standard, myLibrariesCachedMapStorage);
    }

    public void dispose() {
        myLowMemoryWatcher.stop();
    }

    /**
     * The cached contexts of user code and of library code.
     */
    public Map<String, List<TypeEvalContext>> getCachedContexts() {
        return Map.of("user", List.copyOf(myCachedMapStorage.getValue().values()),
            "library", List.copyOf(myLibrariesCachedMapStorage.getValue().values()));
    }

    /**
     * Drops both context maps and the caches of each context in them.
     */
    public void clear() {
        for (List<TypeEvalContext> contexts : getCachedContexts().values()) {
            for (TypeEvalContext context : contexts) {
                ((TypeEvalContextImpl) context).clearCaches();
            }
        }
        myCachedMapStorage.getValue().clear();
        myLibrariesCachedMapStorage.getValue().clear();
    }
}
