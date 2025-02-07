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

package com.jetbrains.python.impl.psi.impl;

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStatementPart;

import jakarta.annotation.Nullable;

/**
 * Common parts functionality.
 * User: dcheryasov
 * Date: Mar 19, 2009 2:51:15 AM
 */
public class PyPartitionedElementImpl extends PyElementImpl {
  public PyPartitionedElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nonnull
  PyStatementPart[] getParts() {
    return childrenToPsi(PyElementTypes.PARTS, PyStatementPart.EMPTY_ARRAY);
  }

  @Nullable
  protected PyStatementPart getPart(PyElementType which) {
    ASTNode n = getNode().findChildByType(which);
    if (n == null) return null;
    return (PyStatementPart)n.getPsi();
  }

  @Nonnull
  protected PyStatementPart getPartNotNull(PyElementType which) {
    ASTNode n = getNode().findChildByType(which);
    assert n != null;
    return (PyStatementPart)n.getPsi();
  }

}
