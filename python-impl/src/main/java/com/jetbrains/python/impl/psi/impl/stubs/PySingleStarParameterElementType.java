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
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PySingleStarParameterImpl;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * @author yole
 */
public class PySingleStarParameterElementType extends PyStubElementType<PySingleStarParameterStub, PySingleStarParameter> {
  public PySingleStarParameterElementType() {
    super("SINGLE_STAR_PARAMETER");
  }

  @Override
  public PsiElement createElement(@Nonnull ASTNode node) {
    return new PySingleStarParameterImpl(node);
  }

  @Override
  public PySingleStarParameter createPsi(@Nonnull PySingleStarParameterStub stub) {
    return new PySingleStarParameterImpl(stub);
  }

  @Override
  public PySingleStarParameterStub createStub(@Nonnull PySingleStarParameter psi, StubElement parentStub) {
    return new PySingleStarParameterStubImpl(parentStub);
  }

  public void serialize(@Nonnull PySingleStarParameterStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
  }

  @Nonnull
  public PySingleStarParameterStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PySingleStarParameterStubImpl(parentStub);
  }
}
