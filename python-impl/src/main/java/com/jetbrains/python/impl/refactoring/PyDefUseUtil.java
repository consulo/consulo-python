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
package com.jetbrains.python.impl.refactoring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import consulo.ide.impl.idea.codeInsight.controlflow.ControlFlow;
import consulo.ide.impl.idea.codeInsight.controlflow.ControlFlowUtil;
import consulo.util.lang.Comparing;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyImplicitImportNameDefiner;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;

/**
 * @author Dennis.Ushakov
 */
public class PyDefUseUtil
{
	private PyDefUseUtil()
	{
	}

	@Nonnull
	public static List<consulo.ide.impl.idea.codeInsight.controlflow.Instruction> getLatestDefs(ScopeOwner block, String varName, PsiElement anchor, boolean acceptTypeAssertions, boolean acceptImplicitImports)
	{
		final ControlFlow controlFlow = ControlFlowCache.getControlFlow(block);
		final consulo.ide.impl.idea.codeInsight.controlflow.Instruction[] instructions = controlFlow.getInstructions();
		final PyAugAssignmentStatement augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(anchor);
		if(augAssignment != null)
		{
			anchor = augAssignment;
		}
		int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
		if(instr < 0)
		{
			return Collections.emptyList();
		}
		if(anchor instanceof PyTargetExpression)
		{
			Collection<consulo.ide.impl.idea.codeInsight.controlflow.Instruction> pred = instructions[instr].allPred();
			if(!pred.isEmpty())
			{
				instr = pred.iterator().next().num();
			}
		}
		final Collection<consulo.ide.impl.idea.codeInsight.controlflow.Instruction> result = getLatestDefs(varName, instructions, instr, acceptTypeAssertions, acceptImplicitImports);
		return new ArrayList<>(result);
	}

	private static Collection<consulo.ide.impl.idea.codeInsight.controlflow.Instruction> getLatestDefs(final String varName,
																									   final consulo.ide.impl.idea.codeInsight.controlflow.Instruction[] instructions,
																									   final int instr,
																									   final boolean acceptTypeAssertions,
																									   final boolean acceptImplicitImports)
	{
		final Collection<consulo.ide.impl.idea.codeInsight.controlflow.Instruction> result = new LinkedHashSet<>();
		ControlFlowUtil.iteratePrev(instr, instructions, instruction -> {
			final PsiElement element = instruction.getElement();
			final PyImplicitImportNameDefiner implicit = PyUtil.as(element, PyImplicitImportNameDefiner.class);
			if(instruction instanceof ReadWriteInstruction)
			{
				final ReadWriteInstruction rwInstruction = (ReadWriteInstruction) instruction;
				final ReadWriteInstruction.ACCESS access = rwInstruction.getAccess();
				if(access.isWriteAccess() || acceptTypeAssertions && access.isAssertTypeAccess())
				{
					final String name = elementName(element);
					if(Comparing.strEqual(name, varName))
					{
						result.add(rwInstruction);
						return consulo.ide.impl.idea.codeInsight.controlflow.ControlFlowUtil.Operation.CONTINUE;
					}
				}
			}
			else if(acceptImplicitImports && implicit != null)
			{
				if(!implicit.multiResolveName(varName).isEmpty())
				{
					result.add(instruction);
					return ControlFlowUtil.Operation.CONTINUE;
				}
			}
			return consulo.ide.impl.idea.codeInsight.controlflow.ControlFlowUtil.Operation.NEXT;
		});
		return result;
	}

	@Nullable
	private static String elementName(PsiElement element)
	{
		if(element instanceof PyImportElement)
		{
			return ((PyImportElement) element).getVisibleName();
		}
		if(element instanceof PyReferenceExpression)
		{
			final QualifiedName qname = ((PyReferenceExpression) element).asQualifiedName();
			if(qname != null)
			{
				return qname.toString();
			}
		}
		return element instanceof PyElement ? ((PyElement) element).getName() : null;
	}

	@Nonnull
	public static PsiElement[] getPostRefs(ScopeOwner block, PyTargetExpression var, PyExpression anchor)
	{
		final ControlFlow controlFlow = ControlFlowCache.getControlFlow(block);
		final consulo.ide.impl.idea.codeInsight.controlflow.Instruction[] instructions = controlFlow.getInstructions();
		final int instr = consulo.ide.impl.idea.codeInsight.controlflow.ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
		if(instr < 0)
		{
			return PyElement.EMPTY_ARRAY;
		}
		final boolean[] visited = new boolean[instructions.length];
		final Collection<PyElement> result = Sets.newHashSet();
		for(consulo.ide.impl.idea.codeInsight.controlflow.Instruction instruction : instructions[instr].allSucc())
		{
			getPostRefs(var, instructions, instruction.num(), visited, result);
		}
		return result.toArray(new PyElement[result.size()]);
	}

	private static void getPostRefs(PyTargetExpression var, consulo.ide.impl.idea.codeInsight.controlflow.Instruction[] instructions, int instr, boolean[] visited, Collection<PyElement> result)
	{
		// TODO: Use ControlFlowUtil.process() for forwards CFG traversal
		if(visited[instr])
		{
			return;
		}
		visited[instr] = true;
		if(instructions[instr] instanceof ReadWriteInstruction)
		{
			final ReadWriteInstruction instruction = (ReadWriteInstruction) instructions[instr];
			final PsiElement element = instruction.getElement();
			String name = elementName(element);
			if(Comparing.strEqual(name, var.getName()))
			{
				final ReadWriteInstruction.ACCESS access = instruction.getAccess();
				if(access.isWriteAccess())
				{
					return;
				}
				result.add((PyElement) instruction.getElement());
			}
		}
		for(consulo.ide.impl.idea.codeInsight.controlflow.Instruction instruction : instructions[instr].allSucc())
		{
			getPostRefs(var, instructions, instruction.num(), visited, result);
		}
	}

	public static class InstructionNotFoundException extends RuntimeException
	{
	}
}
