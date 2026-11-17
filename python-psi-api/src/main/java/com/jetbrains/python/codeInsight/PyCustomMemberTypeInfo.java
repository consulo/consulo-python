// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Info to add to type of custom member.
 *
 * @author Ilya.Kazakevich
 */
public class PyCustomMemberTypeInfo<K> {
    private final Map<Key<K>, K> myCustomInfo = new HashMap<>();

    public PyCustomMemberTypeInfo(Key<K> key, K value) {
        this(Collections.singleton(Pair.create(key, value)));
    }

    public PyCustomMemberTypeInfo(Iterable<? extends Pair<Key<K>, K>> customInfo) {
        for (Pair<Key<K>, K> pair : customInfo) {
            myCustomInfo.put(pair.first, pair.second);
        }
    }

    public PyCustomMemberTypeInfo(Map<Key<K>, K> customInfo) {
        myCustomInfo.putAll(customInfo);
    }

    public void fill(UserDataHolder typeToFill) {
        for (Map.Entry<Key<K>, K> entry : myCustomInfo.entrySet()) {
            typeToFill.putUserData(entry.getKey(), entry.getValue());
        }
    }
}
