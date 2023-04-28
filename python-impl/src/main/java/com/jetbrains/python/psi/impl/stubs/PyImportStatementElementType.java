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

package com.jetbrains.python.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyImportStatementImpl;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import javax.annotation.Nonnull;

import java.io.IOException;

/**
 * @author yole
 */
public class PyImportStatementElementType extends PyStubElementType<PyImportStatementStub, PyImportStatement> {
  public PyImportStatementElementType() {
    this("IMPORT_STATEMENT");
  }

  public PyImportStatementElementType(String debugName) {
    super(debugName);
  }

  @Override
  public PsiElement createElement(@Nonnull ASTNode node) {
    return new PyImportStatementImpl(node);
  }

  @Override
  public PyImportStatement createPsi(@Nonnull PyImportStatementStub stub) {
    return new PyImportStatementImpl(stub);
  }

  @Override
  public PyImportStatementStub createStub(@Nonnull PyImportStatement psi, StubElement parentStub) {
    return new PyImportStatementStubImpl(parentStub, getStubElementType());
  }

  public void serialize(@Nonnull PyImportStatementStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
  }

  @Nonnull
  public PyImportStatementStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyImportStatementStubImpl(parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.IMPORT_STATEMENT;
  }
}