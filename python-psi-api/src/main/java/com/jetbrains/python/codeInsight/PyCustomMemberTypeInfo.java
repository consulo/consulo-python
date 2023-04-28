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
package com.jetbrains.python.codeInsight;

import consulo.util.lang.Pair;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Info to add to type of custom member.
 *
 * @author Ilya.Kazakevich
 */
public class PyCustomMemberTypeInfo<K>
{
	@Nonnull
	private final Map<Key<K>, K> myCustomInfo = new HashMap<>();

	public PyCustomMemberTypeInfo(@Nonnull final Key<K> key, @Nonnull final K value)
	{
		this(Collections.singleton(Pair.create(key, value)));
	}

	public PyCustomMemberTypeInfo(@Nonnull final Iterable<Pair<Key<K>, K>> customInfo)
	{
		for(final Pair<Key<K>, K> pair : customInfo)
		{
			myCustomInfo.put(pair.first, pair.second);
		}
	}

	public PyCustomMemberTypeInfo(@Nonnull final Map<Key<K>, K> customInfo)
	{
		myCustomInfo.putAll(customInfo);
	}

	void fill(@Nonnull final UserDataHolder typeToFill)
	{
		for(final Map.Entry<Key<K>, K> entry : myCustomInfo.entrySet())
		{
			typeToFill.putUserData(entry.getKey(), entry.getValue());
		}
	}
}
