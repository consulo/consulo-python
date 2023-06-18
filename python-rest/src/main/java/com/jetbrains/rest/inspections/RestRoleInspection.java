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
package com.jetbrains.rest.inspections;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.impl.ReSTService;
import com.jetbrains.python.psi.*;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.RestUtil;
import com.jetbrains.rest.psi.RestDirectiveBlock;
import com.jetbrains.rest.psi.RestRole;
import com.jetbrains.rest.quickfixes.AddIgnoredRoleFix;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 * <p>
 * Looks for using not defined roles
 */
@ExtensionImpl
public class RestRoleInspection extends RestInspection
{
	@Nonnull
	@Override
	public HighlightDisplayLevel getDefaultLevel()
	{
		return HighlightDisplayLevel.WARNING;
	}

	@Nonnull
	@Override
	public InspectionToolState<?> createStateProvider()
	{
		return new RestRoleInspectionState();
	}

	@Nls
	@Nonnull
	@Override
	public String getDisplayName()
	{
		return RestBundle.message("INSP.role.not.defined");
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return false;
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session, Object state)
	{
		RestRoleInspectionState inspectionState = (RestRoleInspectionState) state;
		return new Visitor(holder, inspectionState.ignoredRoles);
	}

	private class Visitor extends RestInspectionVisitor
	{
		private final ImmutableSet<String> myIgnoredRoles;
		Set<String> mySphinxRoles = new HashSet<>();

		public Visitor(final ProblemsHolder holder, List<String> ignoredRoles)
		{
			super(holder);
			myIgnoredRoles = ImmutableSet.copyOf(ignoredRoles);
			Project project = holder.getProject();
			final Module module = ModuleUtilCore.findModuleForPsiElement(holder.getFile());
			if(module == null)
			{
				return;
			}
			String dir = ReSTService.getInstance(module).getWorkdir();
			if(!dir.isEmpty())
			{
				fillSphinxRoles(dir, project);
			}
		}

		private void fillSphinxRoles(String dir, Project project)
		{
			VirtualFile config = LocalFileSystem.getInstance().findFileByPath((dir.endsWith("/") ? dir : dir + "/") + "conf.py");
			if(config == null)
			{
				return;
			}

			PsiFile configFile = PsiManager.getInstance(project).findFile(config);
			if(configFile instanceof PyFile)
			{
				PyFile file = (PyFile) configFile;
				List<PyFunction> functions = file.getTopLevelFunctions();
				for(PyFunction function : functions)
				{
					if(!"setup".equals(function.getName()))
					{
						continue;
					}
					PyStatementList stList = function.getStatementList();
					PyStatement[] statements = stList.getStatements();
					for(PyElement statement : statements)
					{
						if(statement instanceof PyExpressionStatement)
						{
							statement = ((PyExpressionStatement) statement).getExpression();
						}
						if(statement instanceof PyCallExpression)
						{
							if(((PyCallExpression) statement).isCalleeText("add_role"))
							{
								PyExpression arg = ((PyCallExpression) statement).getArguments()[0];
								if(arg instanceof PyStringLiteralExpression)
								{
									mySphinxRoles.add(((PyStringLiteralExpression) arg).getStringValue());
								}
							}
						}
					}
				}
			}
		}

		@Override
		public void visitRole(final RestRole node)
		{
			RestFile file = (RestFile) node.getContainingFile();

			if(PsiTreeUtil.getParentOfType(node, RestDirectiveBlock.class) != null)
			{
				return;
			}
			final PsiElement sibling = node.getNextSibling();
			if(sibling == null || sibling.getNode().getElementType() != RestTokenTypes.INTERPRETED)
			{
				return;
			}
			if(RestUtil.PREDEFINED_ROLES.contains(node.getText()) || myIgnoredRoles.contains(node.getRoleName()))
			{
				return;
			}

			if(RestUtil.SPHINX_ROLES.contains(node.getText()) || RestUtil.SPHINX_ROLES.contains(":py" + node.getText()) || mySphinxRoles.contains(node.getRoleName()))
			{
				return;
			}

			Set<String> definedRoles = new HashSet<>();

			RestDirectiveBlock[] directives = PsiTreeUtil.getChildrenOfType(file, RestDirectiveBlock.class);
			if(directives != null)
			{
				for(RestDirectiveBlock block : directives)
				{
					if(block.getDirectiveName().equals("role::"))
					{
						PsiElement role = block.getFirstChild().getNextSibling();
						if(role != null)
						{
							String roleName = role.getText().trim();
							int index = roleName.indexOf('(');
							if(index != -1)
							{
								roleName = roleName.substring(0, index);
							}
							definedRoles.add(roleName);
						}
					}
				}
			}
			if(definedRoles.contains(node.getRoleName()))
			{
				return;
			}
			registerProblem(node, "Not defined role '" + node.getRoleName() + "'", new AddIgnoredRoleFix(node.getRoleName()));
		}
	}
}
