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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyExceptPartImpl;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import javax.annotation.Nonnull;

import java.io.IOException;

/**
 * @author yole
 */
public class PyExceptPartElementType extends PyStubElementType<PyExceptPartStub, PyExceptPart> {
  public PyExceptPartElementType() {
    super("EXCEPT_PART");
  }

  @Override
  public PsiElement createElement(@Nonnull ASTNode node) {
    return new PyExceptPartImpl(node);
  }

  @Override
  public PyExceptPart createPsi(@Nonnull PyExceptPartStub stub) {
    return new PyExceptPartImpl(stub);
  }

  @Override
  public PyExceptPartStub createStub(@Nonnull PyExceptPart psi, StubElement parentStub) {
    return new PyExceptPartStubImpl(parentStub);
  }

  @Override
  public void serialize(@Nonnull PyExceptPartStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
  }

  @Nonnull
  @Override
  public PyExceptPartStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyExceptPartStubImpl(parentStub);
  }
}
