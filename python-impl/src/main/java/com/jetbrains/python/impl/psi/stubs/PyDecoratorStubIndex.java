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
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.PyDecorator;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndexKey;

import jakarta.annotation.Nonnull;

/**
 * Python Decorator stub index.
 * Decorators are indexed by name
 *
 * @author Ilya.Kazakevich
 */
@ExtensionImpl
public class PyDecoratorStubIndex extends StringStubIndexExtension<PyDecorator>
{
	/**
	 * Key to search for python decorators
	 */
	public static final StubIndexKey<String, PyDecorator> KEY = StubIndexKey.createIndexKey("Python.Decorator");

	@Nonnull
	@Override
	public StubIndexKey<String, PyDecorator> getKey()
	{
		return KEY;
	}
}
