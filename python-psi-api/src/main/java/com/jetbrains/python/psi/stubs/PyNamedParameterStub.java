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

/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import jakarta.annotation.Nullable;
import consulo.language.psi.stub.NamedStub;
import com.jetbrains.python.psi.PyNamedParameter;

public interface PyNamedParameterStub extends NamedStub<PyNamedParameter>
{
	boolean isPositionalContainer();

	boolean isKeywordContainer();

	boolean hasDefaultValue();

	@Nullable
	String getTypeComment();
}