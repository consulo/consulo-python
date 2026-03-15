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
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyExceptPartImpl;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;

import java.io.IOException;

/**
 * @author yole
 */
public class PyExceptPartElementType extends PyStubElementType<PyExceptPartStub, PyExceptPart> {
  public PyExceptPartElementType() {
    super("EXCEPT_PART");
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new PyExceptPartImpl(node);
  }

  @Override
  public PyExceptPart createPsi(PyExceptPartStub stub) {
    return new PyExceptPartImpl(stub);
  }

  @Override
  public PyExceptPartStub createStub(PyExceptPart psi, StubElement parentStub) {
    return new PyExceptPartStubImpl(parentStub);
  }

  @Override
  public void serialize(PyExceptPartStub stub, StubOutputStream dataStream) throws IOException {
  }

  @Override
  public PyExceptPartStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyExceptPartStubImpl(parentStub);
  }
}
