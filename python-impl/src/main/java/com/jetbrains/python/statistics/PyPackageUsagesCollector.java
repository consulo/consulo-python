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
package com.jetbrains.python.statistics;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author yole
 */
public class PyPackageUsagesCollector extends AbstractApplicationUsagesCollector
{
	@Nonnull
	@Override
	public Set<UsageDescriptor> getProjectUsages(@Nonnull Project project) throws CollectUsagesException
	{
		final Set<UsageDescriptor> result = new HashSet<>();
		for(final Module m : ModuleManager.getInstance(project).getModules())
		{
			final Sdk pythonSdk = PythonSdkType.findPythonSdk(m);
			if(pythonSdk != null)
			{
				ApplicationManager.getApplication().runReadAction(() -> {
					List<PyRequirement> requirements = PyPackageManager.getInstance(pythonSdk).getRequirements(m);
					if(requirements != null)
					{
						Collection<String> packages = new HashSet<>(PyPIPackageUtil.INSTANCE.getPackageNames());
						for(PyRequirement requirement : requirements)
						{
							String name = requirement.getName();
							if(packages.contains(name))
							{
								result.add(new UsageDescriptor(name, 1));
							}
						}
					}
				});
			}
		}
		return result;
	}

	@Nonnull
	@Override
	public String getGroupId()
	{
		return "py-packages";
	}
}
