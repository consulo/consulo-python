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

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyIfPart;

/**
 * PyIfPart that represents an 'elif' part.
 * User: dcheryasov
 * Date: Mar 12, 2009 5:21:11 PM
 */
public class PyIfPartElifImpl extends PyConditionalStatementPartImpl implements PyIfPart {
  public PyIfPartElifImpl(ASTNode astNode) {
    super(astNode);
  }

  public boolean isElif() {
    return true;
  }
}
