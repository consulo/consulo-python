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

package com.jetbrains.python.impl.codeInsight.dataflow;

import consulo.ide.impl.idea.codeInsight.controlflow.Instruction;
import consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.impl.ScopeVariableImpl;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.impl.PyExceptPartNavigator;
import javax.annotation.Nonnull;

import java.util.Map;

/**
 * @author oleg
 */
public class PyReachingDefsDfaInstance implements consulo.ide.impl.idea.codeInsight.dataflow.map.DfaMapInstance<ScopeVariable>
{
  // Use this its own map, because check in PyReachingDefsDfaSemilattice is important
  public static final consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap<ScopeVariable> INITIAL_MAP = new consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap<ScopeVariable>();

  public DFAMap<ScopeVariable> fun(final DFAMap<ScopeVariable> map, final Instruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null || !((PyFile) element.getContainingFile()).getLanguageLevel().isPy3K()){
      return processReducedMap(map, instruction, element);
    }
    // Scope reduction
    final consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap<ScopeVariable> reducedMap = new consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap<ScopeVariable>();
    for (Map.Entry<String, ScopeVariable> entry : map.entrySet()) {
      final ScopeVariable value = entry.getValue();
      // Support PEP-3110. (PY-1408)
      if (value.isParameter()){
        final PsiElement declaration = value.getDeclarations().iterator().next();
        final PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(declaration);
        if (exceptPart != null){
          if (!PsiTreeUtil.isAncestor(exceptPart, element, false)){
            continue;
          }
        }
      } 
      reducedMap.put(entry.getKey(), value);
    }

    return processReducedMap(reducedMap, instruction, element);
  }

  private consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap<ScopeVariable> processReducedMap(consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap<ScopeVariable> map,
																								 final consulo.ide.impl.idea.codeInsight.controlflow.Instruction instruction,
																								 final PsiElement element) {
    String name = null;
    // Process readwrite instruction
    if (instruction instanceof ReadWriteInstruction && ((ReadWriteInstruction)instruction).getAccess().isWriteAccess()) {
      name = ((ReadWriteInstruction)instruction).getName();
    }
    // Processing PyFunction
    else if (element instanceof PyFunction){
      name = ((PyFunction)element).getName();
    }
    if (name == null){
      return map;
    }
    final ScopeVariable variable = map.get(name);

    // Parameter case
    final PsiElement parameterScope = ScopeUtil.getParameterScope(element);
    if (parameterScope != null) {
      final ScopeVariable scopeVariable = new ScopeVariableImpl(name, true, element);
      map = map.asWritable();
      map.put(name, scopeVariable);
    }
    // Local variable case
    else {
      final ScopeVariableImpl scopeVariable;
      final boolean isParameter = variable != null && variable.isParameter();
      if (variable == null) {
        scopeVariable = new ScopeVariableImpl(name, isParameter, element);
      } else {
        scopeVariable = new ScopeVariableImpl(name, isParameter, variable.getDeclarations());
      }
      map = map.asWritable();
      map.put(name, scopeVariable);
    }
    return map;
  }

  @Nonnull
  public consulo.ide.impl.idea.codeInsight.dataflow.map.DFAMap<ScopeVariable> initial() {
    return INITIAL_MAP;
  }

  public boolean isForward() {
    return true;
  }
}
