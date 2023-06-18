/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
import consulo.language.psi.stub.StubInputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

public class PyNamedTupleStubType extends CustomTargetExpressionStubType<PyNamedTupleStub>
{
	@Nullable
	@Override
	public PyNamedTupleStub createStub(@Nonnull PyTargetExpression psi)
	{
		return PyNamedTupleStubImpl.create(psi);
	}

	@Nullable
	@Override
	public PyNamedTupleStub deserializeStub(@Nonnull StubInputStream stream) throws IOException
	{
		return PyNamedTupleStubImpl.deserialize(stream);
	}
}
