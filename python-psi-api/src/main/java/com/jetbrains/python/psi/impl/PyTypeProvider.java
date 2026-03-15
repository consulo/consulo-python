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
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;

import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyTypeProvider
{
	ExtensionPointName<PyTypeProvider> EP_NAME = ExtensionPointName.create(PyTypeProvider.class);

	@Nullable
	PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context);

	@Nullable
	PyType getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor);

	@Nullable
	Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context);

	@Nullable
	Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context);

	@Nullable
	Ref<PyType> getCallType(PyFunction function, @Nullable PyCallSiteExpression callSite, TypeEvalContext context);

	@Nullable
	PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context);

	@Nullable
	PyType getCallableType(PyCallable callable, TypeEvalContext context);
}
