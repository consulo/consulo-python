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

package com.jetbrains.python.psi.impl.references;

import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFromImportStatement;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

/**
 * Reference to an imported name in a 'from ... import' statement:<br/>
 * <code>from foo import <u>name</u></code>
 *
 * @author yole
 */
public class PyFromImportNameReference extends PyImportReference {
  private final PyFromImportStatement myStatement;

  public PyFromImportNameReference(PyReferenceExpressionImpl element, PyResolveContext context) {
    super(element, context);
    myStatement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
    assert myStatement != null;
  }

  @Nonnull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    QualifiedName qName = myElement.asQualifiedName();
    return qName == null
           ? Collections.<RatedResolveResult>emptyList()
           : ResolveImportUtil.resolveNameInFromImport(myStatement, qName);
  }
}
