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

import java.util.List;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public interface PyImportStatementBase extends PyStatement
{
	/**
	 * @return elements that constitute the "import" clause
	 */
	@Nonnull
	PyImportElement[] getImportElements();

	/**
	 * @return qualified names of imported elements regardless way they were imported.
	 * "from bar import foo" or "import bar.foo" or "from bar import foo as spam" are all "bar.foo"
	 */
	@Nonnull
	List<String> getFullyQualifiedObjectNames();
}
