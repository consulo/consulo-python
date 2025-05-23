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

package com.jetbrains.python.impl.codeInsight.dataflow.scope;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.impl.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyListCompExpressionNavigator;
import com.jetbrains.python.psi.*;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.stub.StubElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;

import static consulo.language.psi.util.PsiTreeUtil.getParentOfType;
import static consulo.language.psi.util.PsiTreeUtil.isAncestor;

/**
 * @author oleg
 */
public class ScopeUtil {
  private ScopeUtil() {
  }

  @Nullable
  public static PsiElement getParameterScope(final PsiElement element){
    if (element instanceof PyNamedParameter){
      final PyFunction function = getParentOfType(element, PyFunction.class, false);
      if (function != null){
        return function;
      }
    }

    final PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element);
    if (exceptPart != null){
      return exceptPart;
    }

    final PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
    if (forStatement != null){
      return forStatement;
    }

    final PyListCompExpression listCompExpression = PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element);
    if (listCompExpression != null){
      return listCompExpression;
    }
    return null;
  }

  /**
   * Return the scope owner for the element.
   *
   * Scope owner is not always the first ScopeOwner parent of the element. Some elements are resolved in outer scopes.
   */
  @Nullable
  public static ScopeOwner getScopeOwner(@Nullable PsiElement element) {
    if (element instanceof StubBasedPsiElement) {
      final StubElement stub = ((StubBasedPsiElement)element).getStub();
      if (stub != null) {
        StubElement parentStub = stub.getParentStub();
        while (parentStub != null) {
          final PsiElement parent = parentStub.getPsi();
          if (parent instanceof ScopeOwner) {
            return (ScopeOwner)parent;
          }
          parentStub = parentStub.getParentStub();
        }
        return null;
      }
    }
    final ScopeOwner firstOwner = getParentOfType(element, ScopeOwner.class);
    if (firstOwner == null) {
      return null;
    }
    final ScopeOwner nextOwner = getParentOfType(firstOwner, ScopeOwner.class);
    // References in decorator expressions are resolved outside of the function (if the lambda is not inside the decorator)
    final PyElement decoratorAncestor = getParentOfType(element, PyDecorator.class);
    if (decoratorAncestor != null && !isAncestor(decoratorAncestor, firstOwner, true)) {
      return nextOwner;
    }
    // References in default values of parameters are resolved outside of the function (if the lambda is not inside the default value)
    final PyParameter parameterAncestor = getParentOfType(element, PyParameter.class);
    if (parameterAncestor != null && !isAncestor(parameterAncestor, firstOwner, true)) {
      final PyExpression defaultValue = parameterAncestor.getDefaultValue();
      if (element != null && isAncestor(defaultValue, element, false)) {
        return nextOwner;
      }
    }
    // Superclasses are resolved outside of the class
    final PyClass containingClass = getParentOfType(element, PyClass.class);
    if (containingClass != null && element != null &&
        isAncestor(containingClass.getSuperClassExpressionList(), element, false)) {
      return nextOwner;
    }
    return firstOwner;
  }

  @Nullable
  public static ScopeOwner getDeclarationScopeOwner(PsiElement anchor, String name) {
    PsiElement element = anchor;
    if (name != null) {
      final ScopeOwner originalScopeOwner = getScopeOwner(element);
      ScopeOwner scopeOwner = originalScopeOwner;
      while (scopeOwner != null) {
        if (!(scopeOwner instanceof PyClass) || scopeOwner == originalScopeOwner) {
          Scope scope = ControlFlowCache.getScope(scopeOwner);
          if (scope.containsDeclaration(name)) {
            return scopeOwner;
          }
        }
        scopeOwner = getScopeOwner(scopeOwner);
      }
    }
    return null;
  }

  @Nonnull
  public static Collection<PsiElement> getReadWriteElements(@Nonnull String name, @Nonnull ScopeOwner scopeOwner, boolean isReadAccess,
                                                            boolean isWriteAccess) {
    ControlFlow flow = ControlFlowCache.getControlFlow(scopeOwner);
    Collection<PsiElement> result = new ArrayList<PsiElement>();
    for (Instruction instr : flow.getInstructions()) {
      if (instr instanceof ReadWriteInstruction) {
        ReadWriteInstruction rw = (ReadWriteInstruction)instr;
        if (name.equals(rw.getName())) {
          ReadWriteInstruction.ACCESS access = rw.getAccess();
          if ((isReadAccess && access.isReadAccess()) || (isWriteAccess && access.isWriteAccess())) {
            result.add(rw.getElement());
          }
        }
      }
    }
    return result;
  }
}
