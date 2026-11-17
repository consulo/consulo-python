// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts the work of the native Python code insight: type evaluations, type contexts, type matches, type walks,
 * overload checks, control flow builds and completion lookup items.
 * <p>
 * The counting is off by default. Then each hook costs one volatile read. A test turns the counting on with {@link #enable},
 * runs an action, and compares two {@link #snapshot} values. The hooks do not change a result.
 * <p>
 * A counter in a {@link #snapshot} is a sum over all threads. {@link #countOnCurrentThread} counts the calling thread only.
 */
public final class PyCodeInsightCounters {
    private PyCodeInsightCounters() {
    }

    /**
     * A counter and its stable name in a {@link Snapshot}.
     */
    public enum Counter {
        GET_TYPE_CALLS("type.getType.calls"),
        GET_TYPE_CACHE_HITS("type.getType.cacheHits"),
        GET_TYPE_EVALUATIONS("type.getType.evaluations"),
        CONTEXTS_CONSTRUCTED("type.context.constructed"),
        CONTEXT_LOOKUP_MISSES("type.context.lookupMisses"),
        ASSUME_TYPE_CALLS("type.assumeType.calls"),
        MATCH_STEPS("type.match.steps"),
        TYPE_TRAVERSAL_VISITS("type.traversal.visits"),
        OVERLOAD_CANDIDATES_CHECKED("overload.candidatesChecked"),
        CFG_BUILDS("cfg.builds"),
        CFG_INSTRUCTIONS("cfg.instructions"),
        COMPLETION_LOOKUP_ITEMS("completion.lookupItems");

        private final String myId;

        Counter(String id) {
            myId = id;
        }

        public String getId() {
            return myId;
        }
    }

    /**
     * The counter values at one moment.
     */
    public record Snapshot(Map<String, Long> counters) {
        public long get(Counter counter) {
            Long value = counters.get(counter.getId());
            return value != null ? value : 0;
        }

        public Snapshot minus(Snapshot before) {
            Map<String, Long> result = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : counters.entrySet()) {
                Long beforeValue = before.counters.get(entry.getKey());
                result.put(entry.getKey(), entry.getValue() - (beforeValue != null ? beforeValue : 0));
            }
            return new Snapshot(result);
        }
    }

    private static volatile boolean enabled = false;
    private static final Object enablerLock = new Object();
    private static int enablers = 0;
    private static final LongAdder[] counters = createCounters();
    private static final ThreadLocal<long[]> threadCounts = new ThreadLocal<>();

    private static LongAdder[] createCounters() {
        LongAdder[] result = new LongAdder[Counter.values().length];
        Arrays.setAll(result, i -> new LongAdder());
        return result;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void inc(Counter counter) {
        if (enabled) {
            add(counter, 1);
        }
    }

    public static void add(Counter counter, long value) {
        if (!enabled) {
            return;
        }
        counters[counter.ordinal()].add(value);
        long[] counts = threadCounts.get();
        if (counts != null) {
            counts[counter.ordinal()] += value;
        }
    }

    public static Snapshot snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Counter counter : Counter.values()) {
            result.put(counter.getId(), counters[counter.ordinal()].sum());
        }
        return new Snapshot(result);
    }

    /**
     * Runs {@code action} with the counting on and returns the counter values of the calling thread only.
     * The work of other threads does not count. A nested call also adds its counts to the outer call.
     */
    public static Map<Counter, Long> countOnCurrentThread(Runnable action) {
        long[] outer = threadCounts.get();
        long[] counts = new long[Counter.values().length];
        Disposable disposable = Disposable.newDisposable("PyCodeInsightCounters.countOnCurrentThread");
        enable(disposable);
        threadCounts.set(counts);
        try {
            action.run();
        }
        finally {
            threadCounts.set(outer);
            Disposer.dispose(disposable);
        }
        if (outer != null) {
            for (int index = 0; index < counts.length; index++) {
                outer[index] += counts[index];
            }
        }
        Map<Counter, Long> result = new EnumMap<>(Counter.class);
        for (Counter counter : Counter.values()) {
            result.put(counter, counts[counter.ordinal()]);
        }
        return result;
    }

    /**
     * Turns the counting on until {@code parentDisposable} is disposed.
     */
    public static void enable(Disposable parentDisposable) {
        synchronized (enablerLock) {
            enablers++;
            enabled = true;
        }
        // An object, not a lambda: a lambda that captures nothing is one shared instance, and the Disposer keeps an instance once.
        Disposer.register(parentDisposable, new Disposable() {
            @Override
            public void dispose() {
                synchronized (enablerLock) {
                    enablers--;
                    enabled = enablers > 0;
                }
            }
        });
    }
}
