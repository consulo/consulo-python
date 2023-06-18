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
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.index.io.StringRef;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.impl.PyImportElementImpl;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import javax.annotation.Nonnull;

import java.io.IOException;

/**
 * @author yole
 */
public class PyImportElementElementType extends PyStubElementType<PyImportElementStub, PyImportElement> {
  public PyImportElementElementType() {
    this("IMPORT_ELEMENT");
  }

  public PyImportElementElementType(String debugName) {
    super(debugName);
  }

  @Override
  public PsiElement createElement(@Nonnull ASTNode node) {
    return new PyImportElementImpl(node);
  }

  @Override
  public PyImportElement createPsi(@Nonnull PyImportElementStub stub) {
    return new PyImportElementImpl(stub);
  }

  @Override
  public PyImportElementStub createStub(@Nonnull PyImportElement psi, StubElement parentStub) {
    final PyTargetExpression asName = psi.getAsNameElement();
    return new PyImportElementStubImpl(psi.getImportedQName(), asName != null ? asName.getName() : "", parentStub, getStubElementType());
  }

  public void serialize(@Nonnull PyImportElementStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    QualifiedName.serialize(stub.getImportedQName(), dataStream);
    dataStream.writeName(stub.getAsName());
  }

  @Nonnull
  public PyImportElementStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    QualifiedName qName = QualifiedName.deserialize(dataStream);
    StringRef asName = dataStream.readName();
    return new PyImportElementStubImpl(qName, asName.getString(), parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.IMPORT_ELEMENT;
  }
}
