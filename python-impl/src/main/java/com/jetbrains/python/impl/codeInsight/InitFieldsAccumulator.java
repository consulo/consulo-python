// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.types.PyCallableParameter;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Declared in JB {@code PyDataclasses.kt}.
 */
public class InitFieldsAccumulator {
    private final LinkedHashMap<String, PyCallableParameter> myPositionalAliasOrFieldNameParams = new LinkedHashMap<>();
    private final LinkedHashSet<String> myKeywordOnlyAliasOrFieldNameParams = new LinkedHashSet<>();
    private final LinkedHashMap<String, PyCallableParameter> myPositionalFieldNameParams = new LinkedHashMap<>();
    private final LinkedHashSet<String> myKeywordOnlyFieldNameParams = new LinkedHashSet<>();
    private final Set<String> mySeenNames = new HashSet<>();

    private boolean mySeenInit = false;
    private boolean mySeenKeywordOnlyClass = false;

    /**
     * The leaf-most dataclass in the MRO; (the leaf class for which we are building the signature).
     */
    private @Nullable PyDataclassParameters myControllingParameters = null;

    public LinkedHashMap<String, PyCallableParameter> getPositionalAliasOrFieldNameParams() {
        return myPositionalAliasOrFieldNameParams;
    }

    public LinkedHashSet<String> getKeywordOnlyAliasOrFieldNameParams() {
        return myKeywordOnlyAliasOrFieldNameParams;
    }

    public LinkedHashMap<String, PyCallableParameter> getPositionalFieldNameParams() {
        return myPositionalFieldNameParams;
    }

    public LinkedHashSet<String> getKeywordOnlyFieldNameParams() {
        return myKeywordOnlyFieldNameParams;
    }

    public Set<String> getSeenNames() {
        return mySeenNames;
    }

    public boolean getSeenInit() {
        return mySeenInit;
    }

    public void setSeenInit(boolean seenInit) {
        mySeenInit = seenInit;
    }

    public boolean getSeenKeywordOnlyClass() {
        return mySeenKeywordOnlyClass;
    }

    public void setSeenKeywordOnlyClass(boolean seenKeywordOnlyClass) {
        mySeenKeywordOnlyClass = seenKeywordOnlyClass;
    }

    public @Nullable PyDataclassParameters getControllingParameters() {
        return myControllingParameters;
    }

    public void setControllingParameters(@Nullable PyDataclassParameters controllingParameters) {
        myControllingParameters = controllingParameters;
    }
}
