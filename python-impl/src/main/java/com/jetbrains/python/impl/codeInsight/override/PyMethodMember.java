// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.override;

import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.generation.MemberChooserObject;
import consulo.language.editor.generation.PsiElementMemberChooserObject;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.ex.ColoredTextContainer;
import consulo.util.lang.StringUtil;

import javax.swing.*;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyMethodMember extends PsiElementMemberChooserObject implements ClassMember
{
	private final String myFullName;

	private static String buildParameterText(PyCallableParameter parameter, TypeEvalContext context)
	{
		// The type of the receiver (self/cls) is implicit; render it only when it was annotated explicitly.
		if(parameter.isSelf() && !isExplicitlyAnnotated(parameter))
		{
			return parameter.getPresentableText(true, context, ignored -> true);
		}
		return parameter.getPresentableText(true, context);
	}

	private static boolean isExplicitlyAnnotated(PyCallableParameter parameter)
	{
		return parameter.getParameter() instanceof PyNamedParameter namedParameter && namedParameter.getAnnotationValue() != null;
	}

	private static String buildNameFor(PyElement element)
	{
		if(element instanceof PyFunction)
		{
			TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
			List<PyCallableParameter> parameters = ((PyFunction) element).getParameters(context);
			StringBuilder result = new StringBuilder();
			result.append(element.getName()).append('(');
			StringUtil.join(parameters, parameter -> buildParameterText(parameter, context), ", ", result);
			result.append(')');
			return result.toString();
		}
		return element.getName();
	}

	public PyMethodMember(PyElement element)
	{
		super(element, trimUnderscores(buildNameFor(element)), IconDescriptorUpdaters.getIcon(element, 0));
		myFullName = buildNameFor(element);
	}

	public static String trimUnderscores(String s)
	{
		return StringUtil.trimStart(StringUtil.trimStart(s, "_"), "_");
	}

	public MemberChooserObject getParentNodeDelegate()
	{
		PyElement element = (PyElement) getPsiElement();
		PyClass parent = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
		assert (parent != null);
		return new PyMethodMember(parent);
	}

	@Override
	public void renderTreeNode(ColoredTextContainer component, JTree tree)
	{
		component.append(myFullName, getTextAttributes(tree));
		component.setIcon(IconDescriptorUpdaters.getIcon(getPsiElement(), 0));
	}
}
