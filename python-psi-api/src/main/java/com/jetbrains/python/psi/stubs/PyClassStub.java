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

/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.psi.stub.NamedStub;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;

public interface PyClassStub extends NamedStub<PyClass>
{

	/**
	 * @return a {@code Map} which contains imported class names as keys and their original names as values
	 */
	@Nonnull
	Map<QualifiedName, QualifiedName> getSuperClasses();

	@Nullable
	QualifiedName getMetaClass();

	@Nullable
	List<String> getSlots();

	@Nullable
	String getDocString();
}