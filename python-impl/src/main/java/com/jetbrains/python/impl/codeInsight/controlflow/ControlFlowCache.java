/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.impl.ScopeImpl;
import consulo.language.controlFlow.ControlFlow;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.SoftReference;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class ControlFlowCache {
  private static Key<SoftReference<ControlFlow>> CONTROL_FLOW_KEY = Key.create("com.jetbrains.python.codeInsight.controlflow.ControlFlow");
  private static Key<SoftReference<Scope>> SCOPE_KEY = Key.create("com.jetbrains.python.codeInsight.controlflow.Scope");

  private ControlFlowCache() {
  }

  public static void clear(ScopeOwner scopeOwner) {
    scopeOwner.putUserData(CONTROL_FLOW_KEY, null);
    scopeOwner.putUserData(SCOPE_KEY, null);
  }

  public static ControlFlow getControlFlow(@Nonnull ScopeOwner element) {
    SoftReference<ControlFlow> ref = element.getUserData(CONTROL_FLOW_KEY);
    ControlFlow flow = ref != null ? ref.get() : null;
    if (flow == null) {
      flow = new PyControlFlowBuilder().buildControlFlow(element);
      element.putUserData(CONTROL_FLOW_KEY, new SoftReference<ControlFlow>(flow));
    }
    return flow;
  }

  @Nonnull
  public static Scope getScope(@Nonnull ScopeOwner element) {
    SoftReference<Scope> ref = element.getUserData(SCOPE_KEY);
    Scope scope = ref != null ? ref.get() : null;
    if (scope == null) {
      scope = new ScopeImpl(element);
      element.putUserData(SCOPE_KEY, new SoftReference<Scope>(scope));
    }
    return scope;
  }
}
