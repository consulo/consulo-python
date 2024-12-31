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

import com.jetbrains.python.psi.resolve.RatedResolveResult;

/**
 * Name definer that defines names imported somehow from other modules.
 *
 * @author vlan
 */
public interface PyImportedNameDefiner extends PyElement
{
	/**
	 * Iterate over possibly resolved PSI elements available via this imported name definer.
	 * <p>
	 * TODO: Make the semantics of the returned elements clearer.
	 */
	@Nonnull
	Iterable<PyElement> iterateNames();

	/**
	 * Return the resolved PSI element available via this imported name definer.
	 */
	@Nonnull
	List<RatedResolveResult> multiResolveName(@Nonnull String name);
}
