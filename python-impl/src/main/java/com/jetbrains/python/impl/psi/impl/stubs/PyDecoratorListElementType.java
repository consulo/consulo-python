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
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyDecoratorListImpl;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;

import java.io.IOException;

/**
 * @author dcheryasov
 * @since 2008-09-28
 */
public class PyDecoratorListElementType extends PyStubElementType<PyDecoratorListStub, PyDecoratorList> {

  public PyDecoratorListElementType() {
    super("DECORATOR_LIST");
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new PyDecoratorListImpl(node);
  }

  @Override
  public PyDecoratorList createPsi(PyDecoratorListStub stub) {
    return new PyDecoratorListImpl(stub);
  }

  @Override
  public PyDecoratorListStub createStub(PyDecoratorList psi, StubElement parentStub) {
    return new PyDecoratorListStubImpl(parentStub);
  }

  @Override
  public void serialize(PyDecoratorListStub stub, StubOutputStream dataStream) throws IOException {
    // nothing
  }

  @Override
  public PyDecoratorListStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyDecoratorListStubImpl(parentStub);
  }
}
