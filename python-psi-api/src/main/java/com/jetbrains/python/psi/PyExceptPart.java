/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import jakarta.annotation.Nullable;
import consulo.language.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;

/**
 * @author dcheryasov
 */
public interface PyExceptPart extends PyElement, StubBasedPsiElement<PyExceptPartStub>, PyNamedElementContainer, PyStatementPart
{
	PyExceptPart[] EMPTY_ARRAY = new PyExceptPart[0];

	@Nullable
	PyExpression getExceptClass();

	@Nullable
	PyExpression getTarget();
}
