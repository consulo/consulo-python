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

import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;

/**
 * Implementation does nothing but marking the element type. 
 * User: dcheryasov
 * Date: Jul 6, 2009 1:33:08 AM
 */
public class PyTupleParameterStubImpl extends StubBase<PyTupleParameter>  implements PyTupleParameterStub {
  private final boolean myHasDefaultValue;

  protected PyTupleParameterStubImpl(boolean hasDefaultValue, StubElement parent) {
    super(parent, PyElementTypes.TUPLE_PARAMETER);
    myHasDefaultValue = hasDefaultValue;
  }

  public boolean hasDefaultValue() {
    return myHasDefaultValue;
  }
}
