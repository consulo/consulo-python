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
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataManager;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.ui.ex.awt.JBList;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static consulo.ide.impl.idea.util.containers.ContainerUtilRt.addIfNotNull;

/**
 * User : ktisha
 */
@ExtensionImpl
public class PyPep8NamingInspection extends PyInspection
{
	private static final Pattern LOWERCASE_REGEX = Pattern.compile("[_\\p{javaLowerCase}][_\\p{javaLowerCase}0-9]*");
	private static final Pattern UPPERCASE_REGEX = Pattern.compile("[_\\p{javaUpperCase}][_\\p{javaUpperCase}0-9]*");
	private static final Pattern MIXEDCASE_REGEX = Pattern.compile("_?_?[\\p{javaUpperCase}][\\p{javaLowerCase}\\p{javaUpperCase}0-9]*");
	private static final String INSPECTION_SHORT_NAME = "PyPep8NamingInspection";
	// See error codes of the tool "pep8-naming"
	private static final Map<String, String> ERROR_CODES_DESCRIPTION =
			ImmutableMap.<String, String>builder().put("N801", "Class names should use CamelCase convention")
					.put("N802", "Function name " + "should be lowercase")
					.put("N803", "Argument name should be lowercase")
					.put("N806", "Variable in function should be lowercase")
					.put("N811", "Constant variable imported as non constant")
					.put("N812", "Lowercase variable imported as non lowercase")
					.put("N813", "CamelCase variable imported as lowercase")
					.put("N814", "CamelCase variable imported as constant")
					.build();

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
										  boolean isOnTheFly,
										  @Nonnull LocalInspectionToolSession session,
										  Object state)
	{
		return new Visitor(holder, session, (PyPep8NamingInspectionState) state);
	}

	@Nonnull
	@Override
	public String getDisplayName()
	{
		return "PEP 8 naming convention violation";
	}

	@Nonnull
	@Override
	public InspectionToolState<?> createStateProvider()
	{
		return new PyPep8NamingInspectionState();
	}

	public class Visitor extends PyInspectionVisitor
	{
		private final PyPep8NamingInspectionState myState;

		public Visitor(ProblemsHolder holder, LocalInspectionToolSession session, PyPep8NamingInspectionState state)
		{
			super(holder, session);
			myState = state;
		}

		@Override
		public void visitPyAssignmentStatement(PyAssignmentStatement node)
		{
			final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyClass.class);
			if(function == null)
			{
				return;
			}
			final Scope scope = ControlFlowCache.getScope(function);
			for(Pair<PyExpression, PyExpression> pair : node.getTargetsToValuesMapping())
			{
				final String name = pair.getFirst().getName();
				if(name == null || scope.isGlobal(name))
				{
					continue;
				}
				if(pair.getFirst() instanceof PyTargetExpression)
				{
					final PyExpression qualifier = ((PyTargetExpression) pair.getFirst()).getQualifier();
					if(qualifier != null)
					{
						return;
					}
				}

				final PyCallExpression assignedValue = PyUtil.as(pair.getSecond(), PyCallExpression.class);
				if(assignedValue != null && assignedValue.getCallee() != null && PyNames.NAMEDTUPLE.equals(assignedValue.getCallee().getName()))
				{
					return;
				}
				final String errorCode = "N806";
				if(!LOWERCASE_REGEX.matcher(name).matches() && !name.startsWith("_") && !myState.ignoredErrors.contains(errorCode))
				{
					registerAndAddRenameAndIgnoreErrorQuickFixes(pair.getFirst(), errorCode);
				}
			}
		}

		@Override
		public void visitPyParameter(PyParameter node)
		{
			final String name = node.getName();
			if(name == null)
			{
				return;
			}

			final String errorCode = "N803";
			if(!LOWERCASE_REGEX.matcher(name).matches() && !myState.ignoredErrors.contains(errorCode))
			{
				registerAndAddRenameAndIgnoreErrorQuickFixes(node, errorCode);
			}
		}

		private void registerAndAddRenameAndIgnoreErrorQuickFixes(@Nullable final PsiElement node, @Nonnull final String errorCode)
		{
			if(getHolder() != null && getHolder().isOnTheFly())
			{
				registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode), new PyRenameElementQuickFix(), new IgnoreErrorFix(errorCode));
			}
			else
			{
				registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode), new IgnoreErrorFix(errorCode));
			}
		}

		@Override
		public void visitPyFunction(PyFunction function)
		{
			final PyClass containingClass = function.getContainingClass();
			if(myState.ignoreOverriddenFunctions && isOverriddenMethod(function))
			{
				return;
			}
			final String name = function.getName();
			if(name == null)
			{
				return;
			}
			if(containingClass != null && (PyUtil.isSpecialName(name) || isIgnoredOrHasIgnoredAncestor(containingClass)))
			{
				return;
			}
			if(!LOWERCASE_REGEX.matcher(name).matches())
			{
				final ASTNode nameNode = function.getNameNode();
				if(nameNode != null)
				{
					final List<LocalQuickFix> quickFixes = Lists.newArrayList();
					if(getHolder() != null && getHolder().isOnTheFly())
					{
						quickFixes.add(new PyRenameElementQuickFix());
					}

					if(containingClass != null)
					{
						quickFixes.add(new IgnoreBaseClassQuickFix(containingClass, myTypeEvalContext));
					}
					final String errorCode = "N802";
					if(!myState.ignoredErrors.contains(errorCode))
					{
						quickFixes.add(new IgnoreErrorFix(errorCode));
						registerProblem(nameNode.getPsi(),
								ERROR_CODES_DESCRIPTION.get(errorCode),
								quickFixes.toArray(new LocalQuickFix[quickFixes.size()]));
					}
				}
			}
		}

		private boolean isOverriddenMethod(@Nonnull PyFunction function)
		{
			return PySuperMethodsSearch.search(function, myTypeEvalContext).findFirst() != null;
		}

		private boolean isIgnoredOrHasIgnoredAncestor(@Nonnull PyClass pyClass)
		{
			final Set<String> blackList = Sets.newHashSet(myState.ignoredBaseClasses);
			if(blackList.contains(pyClass.getQualifiedName()))
			{
				return true;
			}
			for(PyClassLikeType ancestor : pyClass.getAncestorTypes(myTypeEvalContext))
			{
				if(ancestor != null && blackList.contains(ancestor.getClassQName()))
				{
					return true;
				}
			}
			return false;
		}

		@Override
		public void visitPyClass(PyClass node)
		{
			final String name = node.getName();
			if(name == null)
			{
				return;
			}
			final String errorCode = "N801";
			if(!myState.ignoredErrors.contains(errorCode))
			{
				final boolean isLowercaseContextManagerClass = isContextManager(node) && LOWERCASE_REGEX.matcher(name).matches();
				if(!isLowercaseContextManagerClass && !MIXEDCASE_REGEX.matcher(name).matches())
				{
					final ASTNode nameNode = node.getNameNode();
					if(nameNode != null)
					{
						registerAndAddRenameAndIgnoreErrorQuickFixes(nameNode.getPsi(), errorCode);
					}
				}
			}
		}

		private boolean isContextManager(PyClass node)
		{
			final String[] contextManagerFunctionNames = {
					PyNames.ENTER,
					PyNames.EXIT
			};
			for(String name : contextManagerFunctionNames)
			{
				if(node.findMethodByName(name, false, myTypeEvalContext) == null)
				{
					return false;
				}
			}
			return true;
		}

		@Override
		public void visitPyImportElement(PyImportElement node)
		{
			final String asName = node.getAsName();
			final QualifiedName importedQName = node.getImportedQName();
			if(importedQName == null)
			{
				return;
			}
			final String name = importedQName.getLastComponent();

			if(asName == null || name == null)
			{
				return;
			}
			if(UPPERCASE_REGEX.matcher(name).matches())
			{
				final String errorCode = "N811";
				if(!UPPERCASE_REGEX.matcher(asName).matches() && !myState.ignoredErrors.contains(errorCode))
				{
					registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
				}
			}
			else if(LOWERCASE_REGEX.matcher(name).matches())
			{
				final String errorCode = "N812";
				if(!LOWERCASE_REGEX.matcher(asName).matches() && !myState.ignoredErrors.contains(errorCode))
				{
					registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
				}
			}
			else if(LOWERCASE_REGEX.matcher(asName).matches())
			{
				final String errorCode = "N813";
				if(!myState.ignoredErrors.contains(errorCode))
				{
					registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
				}
			}
			else if(UPPERCASE_REGEX.matcher(asName).matches())
			{
				final String errorCode = "N814";
				if(!myState.ignoredErrors.contains(errorCode))
				{
					registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
				}
			}
		}
	}

	private static class IgnoreBaseClassQuickFix implements LocalQuickFix
	{
		private final List<String> myBaseClassNames;

		public IgnoreBaseClassQuickFix(@Nonnull PyClass baseClass, @Nonnull TypeEvalContext context)
		{
			myBaseClassNames = new ArrayList<>();
			ContainerUtil.addIfNotNull(getBaseClassNames(), baseClass.getQualifiedName());
			for(PyClass ancestor : baseClass.getAncestorClasses(context))
			{
				ContainerUtil.addIfNotNull(getBaseClassNames(), ancestor.getQualifiedName());
			}
		}

		@Nonnull
		@Override
		public String getFamilyName()
		{
			return "Ignore method names for descendants of class";
		}

		@Override
		public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
		{
			final JBList list = new JBList(getBaseClassNames());
			final Runnable updateBlackList = () ->
			{
				final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
				profile.<PyPep8NamingInspection, PyPep8NamingInspectionState>modifyToolSettings(PyPep8NamingInspection.class.getSimpleName(), descriptor.getPsiElement(), (i, s) ->
				{
					addIfNotNull(s.ignoredBaseClasses, (String) list.getSelectedValue());
				});
			};

			DataManager.getInstance()
					.getDataContextFromFocus()
					.doWhenDone(dataContext -> new PopupChooserBuilder(list).setTitle("Ignore base class")
							.setItemChoosenCallback(updateBlackList)
							.setFilteringEnabled(o -> (String) o)
							.createPopup()
							.showInBestPositionFor(dataContext));
		}

		public List<String> getBaseClassNames()
		{
			return myBaseClassNames;
		}
	}

	private static class IgnoreErrorFix implements LocalQuickFix
	{
		private final String myCode;
		private static final String myText = "Ignore errors like this";

		public IgnoreErrorFix(String code)
		{
			myCode = code;
		}

		@Nls
		@Nonnull
		@Override
		public String getFamilyName()
		{
			return myText;
		}

		@Override
		public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
		{
			final PsiFile file = descriptor.getStartElement().getContainingFile();
			InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
			profile.<PyPep8NamingInspection, PyPep8NamingInspectionState>modifyToolSettings(INSPECTION_SHORT_NAME, file, (i, s) ->
			{
				if(!s.ignoredErrors.contains(myCode))
				{
					s.ignoredErrors.add(myCode);
				}
			});
		}
	}
}
