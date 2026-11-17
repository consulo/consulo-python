// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.PyLanguageFacadeKt;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.impl.ScopeImpl;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.util.dataholder.Key;

import java.lang.ref.SoftReference;

public final class ControlFlowCache {
    private static final Key<SoftReference<PyControlFlow>> CONTROL_FLOW_KEY =
        Key.create("com.jetbrains.python.codeInsight.controlflow.ControlFlow");
    private static final Key<SoftReference<Scope>> SCOPE_KEY =
        Key.create("com.jetbrains.python.codeInsight.controlflow.Scope");

    private ControlFlowCache() {
    }

    public static void clear(ScopeOwner scopeOwner) {
        scopeOwner.putUserData(CONTROL_FLOW_KEY, null);
        scopeOwner.putUserData(SCOPE_KEY, null);
    }

    public static PyControlFlow getControlFlow(ScopeOwner element, PyControlFlowBuilder controlFlowBuilder) {
        SoftReference<PyControlFlow> ref = element.getUserData(CONTROL_FLOW_KEY);
        PyControlFlow flow = consulo.util.lang.ref.SoftReference.dereference(ref);
        if (flow == null) {
            flow = controlFlowBuilder.buildControlFlow(element);
            element.putUserData(CONTROL_FLOW_KEY, new SoftReference<>(flow));
        }
        return flow;
    }

    public static PyControlFlow getControlFlow(ScopeOwner element) {
        LanguageLevel languageLevel = PyLanguageFacadeKt.getEffectiveLanguageLevel(element.getContainingFile());
        return getControlFlow(element, new PyControlFlowBuilder(languageLevel));
    }

    public static Scope getScope(ScopeOwner element) {
        SoftReference<Scope> ref = element.getUserData(SCOPE_KEY);
        Scope scope = consulo.util.lang.ref.SoftReference.dereference(ref);
        if (scope == null) {
            scope = new ScopeImpl(element);
            element.putUserData(SCOPE_KEY, new SoftReference<>(scope));
        }
        return scope;
    }

    public static PyDataFlow getDataFlow(ScopeOwner element, FlowContext context) {
        // Cache will reset on psi modification, same as TypeEvalContext
        return PyUtil.getParameterizedCachedValue(element, context, ctx -> new PyDataFlow(getControlFlow(element), ctx));
    }
}
