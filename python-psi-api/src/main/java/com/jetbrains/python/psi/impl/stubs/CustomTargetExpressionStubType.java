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

import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubInputStream;

import jakarta.annotation.Nullable;
import java.io.IOException;

/**
 * @author yole
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class CustomTargetExpressionStubType<T extends CustomTargetExpressionStub> {
  public static ExtensionPointName<CustomTargetExpressionStubType> EP_NAME = ExtensionPointName.create(CustomTargetExpressionStubType.class);

  @Nullable
  public abstract T createStub(PyTargetExpression psi);

  @Nullable
  public abstract T deserializeStub(StubInputStream stream) throws IOException;

  public void indexStub(PyTargetExpressionStub stub, IndexSink sink) {
  }
}
