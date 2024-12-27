/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.lang.ref.Ref;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects unreachable code using control flow graph
 */
@ExtensionImpl
public class PyUnreachableCodeInspection extends PyInspection
{
	@Nls
	@Nonnull
	public String getDisplayName()
	{
		return PyBundle.message("INSP.NAME.unreachable.code");
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder,
										  boolean isOnTheFly,
										  @Nonnull LocalInspectionToolSession session,
										  Object state)
	{
		return new Visitor(holder, session);
	}

	public static class Visitor extends PyInspectionVisitor
	{
		public Visitor(@Nonnull ProblemsHolder holder, @Nonnull LocalInspectionToolSession session)
		{
			super(holder, session);
		}

		@Override
		public void visitElement(final PsiElement element)
		{
			if(element instanceof ScopeOwner)
			{
				final ControlFlow flow = ControlFlowCache.getControlFlow((ScopeOwner) element);
				final Instruction[] instructions = flow.getInstructions();
				final List<PsiElement> unreachable = new ArrayList<PsiElement>();
				if(instructions.length > 0)
				{
					ControlFlowUtil.iteratePrev(instructions.length - 1, instructions, instruction ->
					{
						if(instruction.allPred().isEmpty() && !isFirstInstruction(instruction))
						{
							unreachable.add(instruction.getElement());
						}
						return ControlFlowUtil.Operation.NEXT;
					});
				}
				for(PsiElement e : unreachable)
				{
					registerProblem(e, PyBundle.message("INSP.unreachable.code"));
				}
			}
		}
	}

	public static boolean hasAnyInterruptedControlFlowPaths(@Nonnull PsiElement element)
	{
		final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
		if(owner != null)
		{
			final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
			final Instruction[] instructions = flow.getInstructions();
			final int start = ControlFlowUtil.findInstructionNumberByElement(instructions, element);
			if(start >= 0)
			{
				final Ref<Boolean> resultRef = Ref.create(false);
				ControlFlowUtil.iteratePrev(start, instructions, instruction ->
				{
					if(instruction.allPred().isEmpty() && !isFirstInstruction(instruction))
					{
						resultRef.set(true);
						return ControlFlowUtil.Operation.BREAK;
					}
					return ControlFlowUtil.Operation.NEXT;
				});
				return resultRef.get();
			}
		}
		return false;
	}

	private static boolean isFirstInstruction(Instruction instruction)
	{
		return instruction.num() == 0;
	}
}
