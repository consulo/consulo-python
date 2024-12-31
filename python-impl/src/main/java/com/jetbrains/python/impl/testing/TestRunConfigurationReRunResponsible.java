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
package com.jetbrains.python.impl.testing;

import java.util.Collection;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.process.ExecutionException;
import consulo.execution.executor.Executor;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.language.psi.PsiElement;

/**
 * Configuration that handles rerun failed tests itself.
 *
 * @author Ilya.Kazakevich
 */
public interface TestRunConfigurationReRunResponsible
{
	/**
	 * Rerun failed tests
	 *
	 * @param executor    test executor
	 * @param environment test environment
	 * @param failedTests a pack of psi elements, indicating failed tests (to retrn)
	 * @return state to run or null if no rerun actions found (i.e. no errors in failedTest, empty etc)
	 * @throws ExecutionException failed to run
	 */
	@Nullable
	RunProfileState rerunTests(@Nonnull final Executor executor, @Nonnull final ExecutionEnvironment environment, @Nonnull Collection<PsiElement> failedTests) throws ExecutionException;
}
