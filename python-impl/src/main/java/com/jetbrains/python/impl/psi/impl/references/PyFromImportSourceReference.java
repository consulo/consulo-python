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

package com.jetbrains.python.impl.psi.impl.references;

import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * Reference to the import source in a 'from ... import' statement:<br/>
 * <code>from <u>foo</u> import name</code>

 * @author yole
 */
public class PyFromImportSourceReference extends PyImportReference {
  private PyFromImportStatement myStatement;
  
  public PyFromImportSourceReference(PyReferenceExpressionImpl element, PyResolveContext context) {
    super(element, context);
    myStatement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
    assert myStatement != null;
  }

  @Nonnull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    List<PsiElement> targets = ResolveImportUtil.resolveFromImportStatementSource(myStatement, myElement.asQualifiedName());
    return ResolveImportUtil.rateResults(targets);
  }

  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    PyExpression qualifier = myElement.getQualifier();
    return qualifier == null ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
  }
}
