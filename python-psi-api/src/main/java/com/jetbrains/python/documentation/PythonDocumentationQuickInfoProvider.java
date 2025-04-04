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
package com.jetbrains.python.documentation;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Allows you to inject quick info into python documentation provider
 *
 * @author Ilya.Kazakevich
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PythonDocumentationQuickInfoProvider
{
	ExtensionPointName<PythonDocumentationQuickInfoProvider> EP_NAME = ExtensionPointName.create(PythonDocumentationQuickInfoProvider.class);

	/**
	 * Return quick info for <strong>original</strong> element.
	 *
	 * @param originalElement original element
	 * @return info (if exists) or null (if another provider should be checked)
	 */
	@Nullable
	String getQuickInfo(@Nonnull PsiElement originalElement);
}
