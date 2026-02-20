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

import com.google.common.collect.Sets;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.*;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public class PyDefUseUtil
{
	private PyDefUseUtil()
	{
	}

	@Nonnull
	public static List<Instruction> getLatestDefs(ScopeOwner block, String varName, PsiElement anchor, boolean acceptTypeAssertions, boolean acceptImplicitImports)
	{
		ControlFlow controlFlow = ControlFlowCache.getControlFlow(block);
		Instruction[] instructions = controlFlow.getInstructions();
		PyAugAssignmentStatement augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(anchor);
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
			Collection<Instruction> pred = instructions[instr].allPred();
			if(!pred.isEmpty())
			{
				instr = pred.iterator().next().num();
			}
		}
		Collection<Instruction> result = getLatestDefs(varName, instructions, instr, acceptTypeAssertions, acceptImplicitImports);
		return new ArrayList<>(result);
	}

	private static Collection<Instruction> getLatestDefs(String varName,
                                                         Instruction[] instructions,
                                                         int instr,
                                                         boolean acceptTypeAssertions,
                                                         boolean acceptImplicitImports)
	{
		Collection<Instruction> result = new LinkedHashSet<>();
		ControlFlowUtil.iteratePrev(instr, instructions, instruction -> {
			PsiElement element = instruction.getElement();
			PyImplicitImportNameDefiner implicit = PyUtil.as(element, PyImplicitImportNameDefiner.class);
			if(instruction instanceof ReadWriteInstruction)
			{
				ReadWriteInstruction rwInstruction = (ReadWriteInstruction) instruction;
				ReadWriteInstruction.ACCESS access = rwInstruction.getAccess();
				if(access.isWriteAccess() || acceptTypeAssertions && access.isAssertTypeAccess())
				{
					String name = elementName(element);
					if(Comparing.strEqual(name, varName))
					{
						result.add(rwInstruction);
						return ControlFlowUtil.Operation.CONTINUE;
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
			return ControlFlowUtil.Operation.NEXT;
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
			QualifiedName qname = ((PyReferenceExpression) element).asQualifiedName();
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
		ControlFlow controlFlow = ControlFlowCache.getControlFlow(block);
		Instruction[] instructions = controlFlow.getInstructions();
		int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
		if(instr < 0)
		{
			return PyElement.EMPTY_ARRAY;
		}
		boolean[] visited = new boolean[instructions.length];
		Collection<PyElement> result = Sets.newHashSet();
		for(Instruction instruction : instructions[instr].allSucc())
		{
			getPostRefs(var, instructions, instruction.num(), visited, result);
		}
		return result.toArray(new PyElement[result.size()]);
	}

	private static void getPostRefs(PyTargetExpression var, Instruction[] instructions, int instr, boolean[] visited, Collection<PyElement> result)
	{
		// TODO: Use ControlFlowUtil.process() for forwards CFG traversal
		if(visited[instr])
		{
			return;
		}
		visited[instr] = true;
		if(instructions[instr] instanceof ReadWriteInstruction)
		{
			ReadWriteInstruction instruction = (ReadWriteInstruction) instructions[instr];
			PsiElement element = instruction.getElement();
			String name = elementName(element);
			if(Comparing.strEqual(name, var.getName()))
			{
				ReadWriteInstruction.ACCESS access = instruction.getAccess();
				if(access.isWriteAccess())
				{
					return;
				}
				result.add((PyElement) instruction.getElement());
			}
		}
		for(Instruction instruction : instructions[instr].allSucc())
		{
			getPostRefs(var, instructions, instruction.num(), visited, result);
		}
	}

	public static class InstructionNotFoundException extends RuntimeException
	{
	}
}
