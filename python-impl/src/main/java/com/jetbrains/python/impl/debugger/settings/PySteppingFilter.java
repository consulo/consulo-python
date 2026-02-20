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
package com.jetbrains.python.impl.debugger.settings;

import jakarta.annotation.Nonnull;
import consulo.project.Project;
import consulo.util.io.FileUtil;

public class PySteppingFilter
{
	private boolean myIsEnabled;
	private
	@Nonnull
	String myFilter;

	public PySteppingFilter()
	{
		myIsEnabled = true;
		myFilter = "";
	}

	public PySteppingFilter(boolean isEnabled, @Nonnull String filter)
	{
		myIsEnabled = isEnabled;
		myFilter = filter;
	}

	public boolean isEnabled()
	{
		return myIsEnabled;
	}

	public void setEnabled(boolean enabled)
	{
		myIsEnabled = enabled;
	}

	@Nonnull
	public String getFilter()
	{
		return myFilter;
	}

	@Nonnull
	public String getAbsolutePlatformIndependentFilter(@Nonnull Project project)
	{
		StringBuilder resultFilter = new StringBuilder();
		String[] filters = myFilter.split(PyDebuggerSettings.FILTERS_DIVIDER);
		for(String filter : filters)
		{
			if(!(FileUtil.isAbsolutePlatformIndependent(filter) || filter.startsWith("*")))
			{
				resultFilter.append(project.getBasePath()).append('/');
			}
			resultFilter.append(filter).append(PyDebuggerSettings.FILTERS_DIVIDER);
		}
		return resultFilter.toString().replace('\\', '/');
	}

	public void setFilter(@Nonnull String filter)
	{
		myFilter = filter;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(!(o instanceof PySteppingFilter))
		{
			return false;
		}

		PySteppingFilter filter = (PySteppingFilter) o;

		if(isEnabled() != filter.isEnabled())
		{
			return false;
		}
		if(!getFilter().equals(filter.getFilter()))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = (isEnabled() ? 1 : 0);
		result = 31 * result + getFilter().hashCode();
		return result;
	}
}
