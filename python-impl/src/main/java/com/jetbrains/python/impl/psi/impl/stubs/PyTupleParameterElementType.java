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
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.impl.psi.impl.PyTupleParameterImpl;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import javax.annotation.Nonnull;

import java.io.IOException;

/**
 * Does actual storing and loading of tuple parameter stub. Not much to do.
 */
public class PyTupleParameterElementType extends PyStubElementType<PyTupleParameterStub, PyTupleParameter> {

  public PyTupleParameterElementType() {
    super("TUPLE_PARAMETER");
  }

  public PsiElement createElement(@Nonnull final ASTNode node) {
    return new PyTupleParameterImpl(node);
  }

  public PyTupleParameter createPsi(@Nonnull PyTupleParameterStub stub) {
    return new PyTupleParameterImpl(stub);
  }

  public PyTupleParameterStub createStub(@Nonnull PyTupleParameter psi, StubElement parentStub) {
    return new PyTupleParameterStubImpl(psi.hasDefaultValue(), parentStub);
  }

  @Nonnull
  public PyTupleParameterStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    boolean hasDefaultValue = dataStream.readBoolean();
    return new PyTupleParameterStubImpl(hasDefaultValue, parentStub);
  }

  public void serialize(@Nonnull PyTupleParameterStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.hasDefaultValue());
  }
}
