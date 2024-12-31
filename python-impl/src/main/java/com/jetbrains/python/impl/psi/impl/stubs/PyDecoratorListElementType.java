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
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Sep 28, 2008
 */
public class PyDecoratorListElementType extends PyStubElementType<PyDecoratorListStub, PyDecoratorList> {

  public PyDecoratorListElementType() {
    super("DECORATOR_LIST");
  }

  public PsiElement createElement(@Nonnull final ASTNode node) {
    return new PyDecoratorListImpl(node);
  }

  public PyDecoratorList createPsi(@Nonnull final PyDecoratorListStub stub) {
    return new PyDecoratorListImpl(stub);
  }

  public PyDecoratorListStub createStub(@Nonnull final PyDecoratorList psi, final StubElement parentStub) {
    return new PyDecoratorListStubImpl(parentStub);
  }

  public void serialize(@Nonnull final PyDecoratorListStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
    // nothing
  }

  @Nonnull
  public PyDecoratorListStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PyDecoratorListStubImpl(parentStub);
  }
}
