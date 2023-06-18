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
package com.jetbrains.python.impl.testing;

import java.util.Map;

import consulo.component.persist.PersistentStateComponent;
import consulo.ide.ServiceManager;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import java.util.HashMap;
import consulo.util.xml.serializer.XmlSerializerUtil;

@State(name = "PyTestFrameworkService", storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml"))
public class PyTestFrameworkService implements PersistentStateComponent<PyTestFrameworkService>
{
	public static PyTestFrameworkService getInstance()
	{
		return ServiceManager.getService(PyTestFrameworkService.class);
	}

	public Map<String, Boolean> SDK_TO_PYTEST = new HashMap<>();
	public Map<String, Boolean> SDK_TO_NOSETEST = new HashMap<>();
	public Map<String, Boolean> SDK_TO_ATTEST = new HashMap<>();

	@Override
	public PyTestFrameworkService getState()
	{
		return this;
	}

	@Override
	public void loadState(PyTestFrameworkService state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
