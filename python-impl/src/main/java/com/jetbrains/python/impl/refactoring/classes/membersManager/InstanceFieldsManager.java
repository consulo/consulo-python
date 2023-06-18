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
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.Collections2;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;

/**
 * @author Ilya.Kazakevich
 */
class InstanceFieldsManager extends FieldsManager
{
	private static final FieldsOnly FIELDS_ONLY = new FieldsOnly();

	// PY-12170

	InstanceFieldsManager()
	{
		super(false);
	}

	@Override
	public boolean hasConflict(@Nonnull final PyTargetExpression member, @Nonnull final PyClass aClass)
	{
		return NamePredicate.hasElementWithSameName(member, aClass.getInstanceAttributes());
	}

	@Override
	protected Collection<PyElement> moveAssignments(@Nonnull final PyClass from, @Nonnull final Collection<PyAssignmentStatement> statements, @Nonnull final PyClass... to)
	{
		//TODO: Copy/paste with ClassFieldsManager. Move to parent?

		final List<PyElement> result = new ArrayList<>();
		for(final PyClass destClass : to)
		{
			result.addAll(copyInstanceFields(statements, destClass));
		}
		// Delete only declarations made in __init__ to prevent PY-12170
		final PyFunction fromInitMethod = PyUtil.getInitMethod(from);
		if(fromInitMethod != null)
		{ // If class has no init method that means all its fields declared in other methods, so nothing to remove
			deleteElements(Collections2.filter(statements, new InitsOnly(fromInitMethod)));
			//We can't leave class constructor with empty body
		}
		return result;
	}

	/**
	 * Copies class' fields in form of assignments (instance fields) to another class.
	 * Creates init method if there is no any
	 *
	 * @param members assignments to copy
	 * @param to      destination
	 * @return newly created fields
	 */
	@Nonnull
	private static List<PyAssignmentStatement> copyInstanceFields(@Nonnull final Collection<PyAssignmentStatement> members, @Nonnull final PyClass to)
	{
		//We need __init__ method, and if there is no any -- we need to create it
		PyFunction toInitMethod = PyUtil.getInitMethod(to);
		if(toInitMethod == null)
		{
			toInitMethod = createInitMethod(to);
		}
		final PyStatementList statementList = toInitMethod.getStatementList();
		return PyClassRefactoringUtil.copyFieldDeclarationToStatement(members, statementList, null);
	}

	/**
	 * Creates init method and adds it to certain class.
	 *
	 * @param to Class where method should be added
	 * @return newly created method
	 */
	//TODO: Move to utils?
	@Nonnull
	private static PyFunction createInitMethod(@Nonnull final PyClass to)
	{
		final PyFunctionBuilder functionBuilder = new PyFunctionBuilder(PyNames.INIT, to);
		functionBuilder.parameter(PyNames.CANONICAL_SELF); //TODO: Take param from codestyle?
		final PyFunction function = functionBuilder.buildFunction(to.getProject(), LanguageLevel.forElement(to));
		return PyClassRefactoringUtil.addMethods(to, true, function).get(0);
	}

	@Override
	protected boolean classHasField(@Nonnull final PyClass pyClass, @Nonnull final String fieldName)
	{
		return pyClass.findInstanceAttribute(fieldName, true) != null;
	}

	@Nonnull
	@Override
	protected Collection<PyTargetExpression> getFieldsByClass(@Nonnull final PyClass pyClass)
	{
		return Collections2.filter(pyClass.getInstanceAttributes(), FIELDS_ONLY);
	}

	private static class InitsOnly extends NotNullPredicate<PyAssignmentStatement>
	{
		@Nonnull
		private final PyFunction myInitMethod;

		private InitsOnly(@Nonnull final PyFunction initMethod)
		{
			myInitMethod = initMethod;
		}

		@Override
		protected boolean applyNotNull(@Nonnull final PyAssignmentStatement input)
		{
			final PyExpression expression = input.getLeftHandSideExpression();
			if(expression == null)
			{
				return false;
			}

			final PyFunction functionWhereDeclared = PsiTreeUtil.getParentOfType(PyUtil.resolveToTheTop(expression), PyFunction.class);
			return myInitMethod.equals(functionWhereDeclared);
		}
	}

	private static class FieldsOnly extends NotNullPredicate<PyTargetExpression>
	{
		@Override
		protected boolean applyNotNull(@Nonnull final PyTargetExpression input)
		{
			return input.getReference().resolve() instanceof PyTargetExpression;
		}
	}
}
