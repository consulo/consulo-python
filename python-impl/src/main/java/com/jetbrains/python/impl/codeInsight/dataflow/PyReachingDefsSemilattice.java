// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.dataflow;

import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.impl.ScopeVariableImpl;
import consulo.language.dataFlow.map.DFAMap;
import consulo.language.dataFlow.map.MapSemilattice;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PyReachingDefsSemilattice implements MapSemilattice<ScopeVariable> {
    @Override
    public boolean eq(DFAMap<ScopeVariable> e1, DFAMap<ScopeVariable> e2) {
        if (e1 == PyReachingDefsDfaInstance.UNREACHABLE_MARKER || e2 == PyReachingDefsDfaInstance.UNREACHABLE_MARKER) {
            return e1 == e2;
        }
        if (e1 == PyReachingDefsDfaInstance.INITIAL_MAP && e2 != PyReachingDefsDfaInstance.INITIAL_MAP ||
            e2 == PyReachingDefsDfaInstance.INITIAL_MAP && e1 != PyReachingDefsDfaInstance.INITIAL_MAP) {
            return false;
        }
        return e1.equals(e2);
    }

    @Override
    // TODO consulo: Semilattice.join(List) missing, platform still declares join(ArrayList)
    public DFAMap<ScopeVariable> join(ArrayList<DFAMap<ScopeVariable>> ins) {
        if (ins.isEmpty()) {
            return DFAMap.empty();
        }
        if (ins.size() == 1) {
            return ins.get(0);
        }

        List<DFAMap<ScopeVariable>> reachable = ContainerUtil.filter(ins, e -> e != PyReachingDefsDfaInstance.UNREACHABLE_MARKER);

        Set<String> resultNames = getResultNames(reachable);
        if (resultNames == null || resultNames.isEmpty()) {
            return new DFAMap<>();
        }

        DFAMap<ScopeVariable> result = new DFAMap<>();
        for (String name : resultNames) {

            boolean isParameter = true;
            Set<PsiElement> declarations = new HashSet<>();

            // iterating over all maps
            for (DFAMap<ScopeVariable> map : reachable) {
                ScopeVariable variable = map.get(name);
                if (variable == null) {
                    continue;
                }
                isParameter = isParameter && variable.isParameter();
                declarations.addAll(variable.getDeclarations());
            }
            ScopeVariable scopeVariable = new ScopeVariableImpl(name, isParameter, declarations);
            result.put(name, scopeVariable);
        }
        return result;
    }

    private static @Nullable Set<String> getResultNames(List<DFAMap<ScopeVariable>> ins) {
        // Compute intersection of all the names
        Set<String> names2Include = null;
        for (DFAMap<ScopeVariable> map : ins) {
            if (map == PyReachingDefsDfaInstance.INITIAL_MAP) {
                continue;
            }
            names2Include = map.intersectKeys(names2Include);
        }
        return names2Include;
    }
}
