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

/*
 * @author max
 */
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyParameterListImpl;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import jakarta.annotation.Nonnull;

import java.io.IOException;

public class PyParameterListElementType extends PyStubElementType<PyParameterListStub, PyParameterList> {
  public PyParameterListElementType() {
    this("PARAMETER_LIST");
  }

  public PyParameterListElementType(String debugName) {
    super(debugName);
  }

  public PyParameterList createPsi(@Nonnull final PyParameterListStub stub) {
    return new PyParameterListImpl(stub);
  }

  public PyParameterListStub createStub(@Nonnull final PyParameterList psi, final StubElement parentStub) {
    return new PyParameterListStubImpl(parentStub, getStubElementType());
  }

  public PsiElement createElement(@Nonnull final ASTNode node) {
    return new PyParameterListImpl(node);
  }

  public void serialize(@Nonnull final PyParameterListStub stub, @Nonnull final StubOutputStream dataStream)
      throws IOException {
  }

  @Nonnull
  public PyParameterListStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PyParameterListStubImpl(parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.PARAMETER_LIST;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    if (node.getTreeParent().getElementType() == PyElementTypes.LAMBDA_EXPRESSION) {
      return false;
    }
    return super.shouldCreateStub(node);
  }
}