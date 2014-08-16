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

package com.jetbrains.python.codeInsight;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.Configurable;

/**
 * @author yole
 */
public class PySpecificSmartKeysOptions extends BeanConfigurable<PyCodeInsightSettings> implements Configurable
{
	public PySpecificSmartKeysOptions()
	{
		super(PyCodeInsightSettings.getInstance());
		checkBox("INSERT_BACKSLASH_ON_WRAP", "Insert backslash when pressing Enter inside a statement");
		checkBox("INSERT_SELF_FOR_METHODS", "Insert 'self' when defining a method");
		checkBox("INSERT_TYPE_DOCSTUB", "Insert 'type' and 'rtype' to the documentation comment stub");
	}

	@Nls
	@Override
	public String getDisplayName()
	{
		return null;
	}

	@Nullable
	@Override
	public String getHelpTopic()
	{
		return null;
	}
}
