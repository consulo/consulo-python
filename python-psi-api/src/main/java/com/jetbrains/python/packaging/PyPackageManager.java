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
package com.jetbrains.python.packaging;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import consulo.util.dataholder.Key;

/**
 * @author yole
 */
public abstract class PyPackageManager
{
	public static final Key<Boolean> RUNNING_PACKAGING_TASKS = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks");

	public static final String USE_USER_SITE = "--user";

	@Nonnull
	public static PyPackageManager getInstance(@Nonnull Sdk sdk)
	{
		return PyPackageManagers.getInstance().forSdk(sdk);
	}

	public abstract void installManagement() throws ExecutionException;

	public abstract boolean hasManagement() throws ExecutionException;

	public abstract void install(@Nonnull String requirementString) throws ExecutionException;

	public abstract void install(@Nonnull List<PyRequirement> requirements, @Nonnull List<String> extraArgs) throws ExecutionException;

	public abstract void uninstall(@Nonnull List<PyPackage> packages) throws ExecutionException;

	public abstract void refresh();

	@Nonnull
	public abstract String createVirtualEnv(@Nonnull String destinationDir, boolean useGlobalSite) throws ExecutionException;

	@Nullable
	public abstract List<PyPackage> getPackages();

	@Nonnull
	public abstract List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException;

	@Nullable
	public abstract List<PyRequirement> getRequirements(@Nonnull Module module);

	@Nonnull
	public abstract Set<PyPackage> getDependents(@Nonnull PyPackage pkg) throws ExecutionException;
}
