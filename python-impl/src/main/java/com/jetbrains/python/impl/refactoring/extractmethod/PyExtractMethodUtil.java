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
package com.jetbrains.python.impl.refactoring.extractmethod;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragment;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.codeFragment.CodeFragment;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.event.RefactoringElementListenerComposite;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.event.RefactoringEventListener;
import consulo.language.editor.refactoring.extractMethod.*;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author oleg
 */
public class PyExtractMethodUtil
{
	public static final String NAME = "extract.method.name";

	private PyExtractMethodUtil()
	{
	}

	public static void extractFromStatements(@Nonnull Project project,
			@Nonnull Editor editor,
			@Nonnull PyCodeFragment fragment,
			@Nonnull PsiElement statement1,
			@Nonnull PsiElement statement2)
	{
		if(!fragment.getOutputVariables().isEmpty() && fragment.isReturnInstructionInside())
		{
			CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.method.error.local.variable.modifications.and.returns"), RefactoringBundle.message("error" +
					".title"), "refactoring.extractMethod");
			return;
		}

		PyFunction function = PsiTreeUtil.getParentOfType(statement1, PyFunction.class);
		PyUtil.MethodFlags flags = function == null ? null : PyUtil.MethodFlags.of(function);
		boolean isClassMethod = flags != null && flags.isClassMethod();
		boolean isStaticMethod = flags != null && flags.isStaticMethod();

		// collect statements
		List<PsiElement> elementsRange = PyPsiUtils.collectElements(statement1, statement2);
		if(elementsRange.isEmpty())
		{
			CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.method.error.empty.fragment"), RefactoringBundle.message("extract.method.title"), "refactoring" +
					".extractMethod");
			return;
		}

		Pair<String, AbstractVariableData[]> data = getNameAndVariableData(project, fragment, statement1, isClassMethod, isStaticMethod);
		if(data.first == null || data.second == null)
		{
			return;
		}

		String methodName = data.first;
		AbstractVariableData[] variableData = data.second;

		SimpleDuplicatesFinder finder = new SimpleDuplicatesFinder(statement1, statement2, fragment.getOutputVariables(), variableData);

		CommandProcessor.getInstance().executeCommand(project, () -> {
			RefactoringEventData beforeData = new RefactoringEventData();
			beforeData.addElements(new PsiElement[]{
					statement1,
					statement2
			});
			project.getMessageBus().syncPublisher(RefactoringEventListener.class).refactoringStarted(getRefactoringId(), beforeData);

			StringBuilder builder = new StringBuilder();
			boolean isAsync = fragment.isAsync();
			if(isAsync)
			{
				builder.append("async ");
			}
			builder.append("def f():\n    ");
			List<PsiElement> newMethodElements = new ArrayList<>(elementsRange);
			boolean hasOutputVariables = !fragment.getOutputVariables().isEmpty();

			PyElementGenerator generator = PyElementGenerator.getInstance(project);
			LanguageLevel languageLevel = LanguageLevel.forElement(statement1);
			if(hasOutputVariables)
			{
				// Generate return modified variables statements
				String outputVariables = StringUtil.join(fragment.getOutputVariables(), ", ");
				String newMethodText = builder + "return " + outputVariables;
				builder.append(outputVariables);

				PyFunction function1 = generator.createFromText(languageLevel, PyFunction.class, newMethodText);
				PsiElement returnStatement = function1.getStatementList().getStatements()[0];
				newMethodElements.add(returnStatement);
			}

			// Generate method
			PyFunction generatedMethod = generateMethodFromElements(project, methodName, variableData, newMethodElements, flags, isAsync);
			PyFunction insertedMethod = WriteAction.compute(() -> insertGeneratedMethod(statement1, generatedMethod));

			// Process parameters
			PsiElement firstElement = elementsRange.get(0);
			boolean isMethod = PyPsiUtils.isMethodContext(firstElement);
			WriteAction.run(() -> {
				processParameters(project, insertedMethod, variableData, isMethod, isClassMethod, isStaticMethod);
				processGlobalWrites(insertedMethod, fragment);
				processNonlocalWrites(insertedMethod, fragment);
			});

			// Generate call element
			if(hasOutputVariables)
			{
				builder.append(" = ");
			}
			else if(fragment.isReturnInstructionInside())
			{
				builder.append("return ");
			}
			if(isAsync)
			{
				builder.append("await ");
			}
			else if(fragment.isYieldInside())
			{
				builder.append("yield from ");
			}
			if(isMethod)
			{
				appendSelf(firstElement, builder, isStaticMethod);
			}
			builder.append(methodName).append("(");
			builder.append(createCallArgsString(variableData)).append(")");
			PyFunction function1 = generator.createFromText(languageLevel, PyFunction.class, builder.toString());
			PsiElement callElement = function1.getStatementList().getStatements()[0];

			// Both statements are used in finder, so should be valid at this moment
			PyPsiUtils.assertValid(statement1);
			PyPsiUtils.assertValid(statement2);
			List<SimpleMatch> duplicates = collectDuplicates(finder, statement1, insertedMethod);

			// replace statements with call
			PsiElement insertedCallElement = WriteAction.compute(() -> replaceElements(elementsRange, callElement));
			insertedCallElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedCallElement);

			if(insertedCallElement != null)
			{
				processDuplicates(duplicates, insertedCallElement, editor);
			}

			// Set editor
			setSelectionAndCaret(editor, insertedCallElement);

			RefactoringEventData afterData = new RefactoringEventData();
			afterData.addElement(insertedMethod);
			project.getMessageBus().syncPublisher(RefactoringEventListener.class).refactoringDone(getRefactoringId(), afterData);
		}, PyBundle.message("refactoring.extract.method"), null);
	}

	@Nonnull
	private static List<SimpleMatch> collectDuplicates(@Nonnull SimpleDuplicatesFinder finder, @Nonnull PsiElement originalScopeAnchor, @Nonnull PyFunction generatedMethod)
	{
		List<PsiElement> scopes = collectScopes(originalScopeAnchor, generatedMethod);
		return ExtractMethodHelper.collectDuplicates(finder, scopes, generatedMethod);
	}

	@Nonnull
	private static List<PsiElement> collectScopes(@Nonnull PsiElement anchor, @Nonnull PyFunction generatedMethod)
	{
		ScopeOwner owner = ScopeUtil.getScopeOwner(anchor);
		if(owner instanceof PsiFile)
		{
			return Collections.emptyList();
		}
		List<PsiElement> scope = new ArrayList<>();
		if(owner instanceof PyFunction)
		{
			scope.add(owner);
			PyClass containingClass = ((PyFunction) owner).getContainingClass();
			if(containingClass != null)
			{
				for(PyFunction function : containingClass.getMethods())
				{
					if(!function.equals(owner) && !function.equals(generatedMethod))
					{
						scope.add(function);
					}
				}
			}
		}
		return scope;
	}

	private static void processDuplicates(@Nonnull List<SimpleMatch> duplicates, @Nonnull PsiElement replacement, @Nonnull Editor editor)
	{
		ExtractMethodHelper.replaceDuplicates(replacement, editor, pair -> replaceElements(pair.first, pair.second.copy()), duplicates);
	}

	private static void processGlobalWrites(@Nonnull PyFunction function, @Nonnull PyCodeFragment fragment)
	{
		Set<String> globalWrites = fragment.getGlobalWrites();
		Set<String> newGlobalNames = new LinkedHashSet<>();
		Scope scope = ControlFlowCache.getScope(function);
		for(String name : globalWrites)
		{
			if(!scope.isGlobal(name))
			{
				newGlobalNames.add(name);
			}
		}
		if(!newGlobalNames.isEmpty())
		{
			PyElementGenerator generator = PyElementGenerator.getInstance(function.getProject());
			PyGlobalStatement globalStatement = generator.createFromText(LanguageLevel.forElement(function), PyGlobalStatement.class, "global " + StringUtil.join(newGlobalNames, ", "));
			PyStatementList statementList = function.getStatementList();
			statementList.addBefore(globalStatement, statementList.getFirstChild());
		}
	}

	private static void processNonlocalWrites(@Nonnull PyFunction function, @Nonnull PyCodeFragment fragment)
	{
		Set<String> nonlocalWrites = fragment.getNonlocalWrites();
		Set<String> newNonlocalNames = new LinkedHashSet<>();
		Scope scope = ControlFlowCache.getScope(function);
		for(String name : nonlocalWrites)
		{
			if(!scope.isNonlocal(name))
			{
				newNonlocalNames.add(name);
			}
		}
		if(!newNonlocalNames.isEmpty())
		{
			PyElementGenerator generator = PyElementGenerator.getInstance(function.getProject());
			PyNonlocalStatement nonlocalStatement = generator.createFromText(LanguageLevel.forElement(function), PyNonlocalStatement.class, "nonlocal " + StringUtil.join(newNonlocalNames, ", " +
					""));
			PyStatementList statementList = function.getStatementList();
			statementList.addBefore(nonlocalStatement, statementList.getFirstChild());
		}
	}


	private static void appendSelf(@Nonnull PsiElement firstElement, @Nonnull StringBuilder builder, boolean staticMethod)
	{
		if(staticMethod)
		{
			PyClass containingClass = PsiTreeUtil.getParentOfType(firstElement, PyClass.class);
			assert containingClass != null;
			builder.append(containingClass.getName());
		}
		else
		{
			builder.append(PyUtil.getFirstParameterName(PsiTreeUtil.getParentOfType(firstElement, PyFunction.class)));
		}
		builder.append(".");
	}

	public static void extractFromExpression(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PyCodeFragment fragment, @Nonnull PsiElement expression)
	{
		if(!fragment.getOutputVariables().isEmpty())
		{
			CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.method.error.local.variable.modifications"), RefactoringBundle.message("error.title"),
					"refactoring.extractMethod");
			return;
		}

		if(fragment.isReturnInstructionInside())
		{
			CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.method.error.returns"), RefactoringBundle.message("error.title"), "refactoring.extractMethod");
			return;
		}

		PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
		PyUtil.MethodFlags flags = function == null ? null : PyUtil.MethodFlags.of(function);
		boolean isClassMethod = flags != null && flags.isClassMethod();
		boolean isStaticMethod = flags != null && flags.isClassMethod();

		Pair<String, AbstractVariableData[]> data = getNameAndVariableData(project, fragment, expression, isClassMethod, isStaticMethod);
		if(data.first == null || data.second == null)
		{
			return;
		}

		String methodName = data.first;
		AbstractVariableData[] variableData = data.second;

		SimpleDuplicatesFinder finder = new SimpleDuplicatesFinder(expression, expression, fragment.getOutputVariables(), variableData);
		if(fragment.getOutputVariables().isEmpty())
		{
			CommandProcessor.getInstance().executeCommand(project, () -> {
				// Generate method
				boolean isAsync = fragment.isAsync();
				PyFunction generatedMethod = generateMethodFromExpression(project, methodName, variableData, expression, flags, isAsync);
				PyFunction insertedMethod = WriteAction.compute(() -> insertGeneratedMethod(expression, generatedMethod));

				// Process parameters
				boolean isMethod = PyPsiUtils.isMethodContext(expression);
				WriteAction.run(() -> processParameters(project, insertedMethod, variableData, isMethod, isClassMethod, isStaticMethod));

				// Generating call element
				StringBuilder builder = new StringBuilder();
				if(isAsync)
				{
					builder.append("async ");
				}
				builder.append("def f():\n    ");
				if(isAsync)
				{
					builder.append("await ");
				}
				else if(fragment.isYieldInside())
				{
					builder.append("yield from ");
				}
				else
				{
					builder.append("return ");
				}
				if(isMethod)
				{
					appendSelf(expression, builder, isStaticMethod);
				}
				builder.append(methodName);
				builder.append("(").append(createCallArgsString(variableData)).append(")");
				PyElementGenerator generator = PyElementGenerator.getInstance(project);
				PyFunction function1 = generator.createFromText(LanguageLevel.forElement(expression), PyFunction.class, builder.toString());
				PyElement generated = function1.getStatementList().getStatements()[0];
				PsiElement callElement;
				if(generated instanceof PyReturnStatement)
				{
					callElement = ((PyReturnStatement) generated).getExpression();
				}
				else if(generated instanceof PyExpressionStatement)
				{
					callElement = ((PyExpressionStatement) generated).getExpression();
				}
				else
				{
					callElement = null;
				}

				PyPsiUtils.assertValid(expression);
				List<SimpleMatch> duplicates = collectDuplicates(finder, expression, insertedMethod);

				// replace statements with call
				PsiElement insertedCallElement = null;
				if(callElement != null)
				{
					insertedCallElement = WriteAction.compute(() -> PyReplaceExpressionUtil.replaceExpression(expression, callElement));
					if(insertedCallElement != null)
					{
						processDuplicates(duplicates, insertedCallElement, editor);
					}
				}
				setSelectionAndCaret(editor, insertedCallElement);
				// Set editor
			}, PyBundle.message("refactoring.extract.method"), null);
		}
	}

	private static void setSelectionAndCaret(@Nonnull Editor editor, @Nullable PsiElement callElement)
	{
		editor.getSelectionModel().removeSelection();
		if(callElement != null)
		{
			int offset = callElement.getTextOffset();
			editor.getCaretModel().moveToOffset(offset);
		}
	}

	@Nonnull
	private static PsiElement replaceElements(@Nonnull List<PsiElement> elementsRange, @Nonnull PsiElement callElement)
	{
		callElement = elementsRange.get(0).replace(callElement);
		if(elementsRange.size() > 1)
		{
			callElement.getParent().deleteChildRange(elementsRange.get(1), elementsRange.get(elementsRange.size() - 1));
		}
		return callElement;
	}

	@Nonnull
	private static PsiElement replaceElements(@Nonnull SimpleMatch match, @Nonnull PsiElement element)
	{
		List<PsiElement> elementsRange = PyPsiUtils.collectElements(match.getStartElement(), match.getEndElement());
		Map<String, String> changedParameters = match.getChangedParameters();
		PsiElement callElement = element;
		PyElementGenerator generator = PyElementGenerator.getInstance(callElement.getProject());
		if(element instanceof PyAssignmentStatement)
		{
			PyExpression value = ((PyAssignmentStatement) element).getAssignedValue();
			if(value != null)
			{
				callElement = value;
			}
			PyExpression[] targets = ((PyAssignmentStatement) element).getTargets();
			if(targets.length == 1)
			{
				String output = match.getChangedOutput();
				PyExpression text = generator.createFromText(LanguageLevel.forElement(callElement), PyAssignmentStatement.class, output + " = 1").getTargets()[0];
				targets[0].replace(text);
			}
		}
		if(element instanceof PyExpressionStatement)
		{
			callElement = ((PyExpressionStatement) element).getExpression();
		}
		if(callElement instanceof PyCallExpression)
		{
			Set<String> keys = changedParameters.keySet();
			PyArgumentList argumentList = ((PyCallExpression) callElement).getArgumentList();
			if(argumentList != null)
			{
				for(PyExpression arg : argumentList.getArguments())
				{
					String argText = arg.getText();
					if(argText != null && keys.contains(argText))
					{
						arg.replace(generator.createExpressionFromText(LanguageLevel.forElement(callElement), changedParameters.get(argText)));
					}
				}
			}
		}
		return replaceElements(elementsRange, element);
	}

	// Creates string for call
	@Nonnull
	private static String createCallArgsString(@Nonnull AbstractVariableData[] variableDatas)
	{
		return StringUtil.join(ContainerUtil.mapNotNull(variableDatas, data -> data.isPassAsParameter() ? data.getOriginalName() : null), ",");
	}

	private static void processParameters(@Nonnull Project project,
			@Nonnull PyFunction generatedMethod,
			@Nonnull AbstractVariableData[] variableData,
			boolean isMethod,
			boolean isClassMethod,
			boolean isStaticMethod)
	{
		Map<String, String> map = createMap(variableData);
		// Rename parameters
		for(PyParameter parameter : generatedMethod.getParameterList().getParameters())
		{
			String name = parameter.getName();
			String newName = map.get(name);
			if(name != null && newName != null && !name.equals(newName))
			{
				Map<PsiElement, String> allRenames = new java.util.HashMap<>();
				allRenames.put(parameter, newName);
				UsageInfo[] usages = RenameUtil.findUsages(parameter, newName, false, false, allRenames);
				try
				{
					RenameUtil.doRename(parameter, newName, usages, project, new RefactoringElementListenerComposite());
				}
				catch(IncorrectOperationException e)
				{
					RenameUtil.showErrorMessage(e, parameter, project);
					return;
				}
			}
		}
		// Change signature according to pass settings and
		PyFunctionBuilder builder = new PyFunctionBuilder("foo", generatedMethod);
		if(isClassMethod)
		{
			builder.parameter("cls");
		}
		else if(isMethod && !isStaticMethod)
		{
			builder.parameter("self");
		}
		for(AbstractVariableData data : variableData)
		{
			if(data.isPassAsParameter())
			{
				builder.parameter(data.getName());
			}
		}
		PyParameterList pyParameterList = builder.buildFunction(project, LanguageLevel.forElement(generatedMethod)).getParameterList();
		generatedMethod.getParameterList().replace(pyParameterList);
	}

	@Nonnull
	private static Map<String, String> createMap(@Nonnull AbstractVariableData[] variableData)
	{
		Map<String, String> map = new HashMap<>();
		for(AbstractVariableData data : variableData)
		{
			map.put(data.getOriginalName(), data.getName());
		}
		return map;
	}

	@Nonnull
	private static PyFunction insertGeneratedMethod(@Nonnull PsiElement anchor, @Nonnull PyFunction generatedMethod)
	{
		Pair<PsiElement, TextRange> data = anchor.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
		if(data != null)
		{
			anchor = data.first;
		}
		PsiNamedElement parent = PsiTreeUtil.getParentOfType(anchor, PyFile.class, PyClass.class, PyFunction.class);

		PsiElement result;
		// The only safe case to insert extracted function *after* the original scope owner is when it's function.
		if(parent instanceof PyFunction)
		{
			result = parent.getParent().addAfter(generatedMethod, parent);
		}
		else
		{
			PsiElement target = parent instanceof PyClass ? ((PyClass) parent).getStatementList() : parent;
			PsiElement insertionAnchor = PyPsiUtils.getParentRightBefore(anchor, target);
			assert insertionAnchor != null;
			Couple<PsiComment> comments = PyPsiUtils.getPrecedingComments(insertionAnchor);
			result = insertionAnchor.getParent().addBefore(generatedMethod, comments != null ? comments.getFirst() : insertionAnchor);
		}
		// to ensure correct reformatting, mark the entire method as generated
		result.accept(new PsiRecursiveElementVisitor()
		{
			@Override
			public void visitElement(@Nonnull PsiElement element)
			{
				super.visitElement(element);
				CodeEditUtil.setNodeGenerated(element.getNode(), true);
			}
		});
		return (PyFunction) result;
	}

	@Nonnull
	private static PyFunction generateMethodFromExpression(@Nonnull Project project,
			@Nonnull String methodName,
			@Nonnull AbstractVariableData[] variableData,
			@Nonnull PsiElement expression,
			@Nullable PyUtil.MethodFlags flags,
			boolean isAsync)
	{
		PyFunctionBuilder builder = new PyFunctionBuilder(methodName, expression);
		addDecorators(builder, flags);
		addFakeParameters(builder, variableData);
		if(isAsync)
		{
			builder.makeAsync();
		}
		String text;
		if(expression instanceof PyYieldExpression)
		{
			text = String.format("(%s)", expression.getText());
		}
		else
		{
			text = expression.getText();
		}
		builder.statement("return " + text);
		return builder.buildFunction(project, LanguageLevel.forElement(expression));
	}

	@Nonnull
	private static PyFunction generateMethodFromElements(@Nonnull Project project,
			@Nonnull String methodName,
			@Nonnull AbstractVariableData[] variableData,
			@Nonnull List<PsiElement> elementsRange,
			@Nullable PyUtil.MethodFlags flags,
			boolean isAsync)
	{
		assert !elementsRange.isEmpty() : "Empty statements list was selected!";

		PyFunctionBuilder builder = new PyFunctionBuilder(methodName, elementsRange.get(0));
		if(isAsync)
		{
			builder.makeAsync();
		}
		addDecorators(builder, flags);
		addFakeParameters(builder, variableData);
		PyFunction method = builder.buildFunction(project, LanguageLevel.forElement(elementsRange.get(0)));
		PyStatementList statementList = method.getStatementList();
		for(PsiElement element : elementsRange)
		{
			if(element instanceof PsiWhiteSpace)
			{
				continue;
			}
			statementList.add(element);
		}
		// remove last instruction
		PsiElement child = statementList.getFirstChild();
		if(child != null)
		{
			child.delete();
		}
		PsiElement last = statementList;
		while(last != null)
		{
			last = last.getLastChild();
			if(last instanceof PsiWhiteSpace)
			{
				last.delete();
			}
		}
		return method;
	}

	private static void addDecorators(@Nonnull PyFunctionBuilder builder, @Nullable PyUtil.MethodFlags flags)
	{
		if(flags != null)
		{
			if(flags.isClassMethod())
			{
				builder.decorate(PyNames.CLASSMETHOD);
			}
			else if(flags.isStaticMethod())
			{
				builder.decorate(PyNames.STATICMETHOD);
			}
		}
	}

	private static void addFakeParameters(@Nonnull PyFunctionBuilder builder, @Nonnull AbstractVariableData[] variableData)
	{
		for(AbstractVariableData data : variableData)
		{
			builder.parameter(data.getOriginalName());
		}
	}

	@Nonnull
	private static Pair<String, AbstractVariableData[]> getNameAndVariableData(@Nonnull final Project project,
			@Nonnull final CodeFragment fragment,
			@Nonnull PsiElement element,
			final boolean isClassMethod,
			final boolean isStaticMethod)
	{
		final ExtractMethodValidator validator = new PyExtractMethodValidator(element, project);
		if(ApplicationManager.getApplication().isUnitTestMode())
		{
			String name = System.getProperty(NAME);
			if(name == null)
			{
				name = "foo";
			}
			String error = validator.check(name);
			if(error != null)
			{
				if(ApplicationManager.getApplication().isUnitTestMode())
				{
					throw new CommonRefactoringUtil.RefactoringErrorHintException(error);
				}
				if(Messages.showOkCancelDialog(error + ". " + RefactoringBundle.message("do.you.wish.to.continue"), RefactoringBundle.message("warning.title"), Messages.getWarningIcon()) != Messages
						.OK)
				{
					throw new CommonRefactoringUtil.RefactoringErrorHintException(error);
				}
			}
			List<AbstractVariableData> data = new ArrayList<>();
			for(String in : fragment.getInputVariables())
			{
				AbstractVariableData d = new AbstractVariableData();
				d.name = in + "_new";
				d.originalName = in;
				d.passAsParameter = true;
				data.add(d);
			}
			return Pair.create(name, data.toArray(new AbstractVariableData[data.size()]));
		}

		final boolean isMethod = PyPsiUtils.isMethodContext(element);
		final ExtractMethodDecorator decorator = new ExtractMethodDecorator()
		{
			@Nonnull
			public String createMethodSignature(String methodName, @Nonnull AbstractVariableData[] variableDatas)
			{
				StringBuilder builder = new StringBuilder();
				if(isClassMethod)
				{
					builder.append("cls");
				}
				else if(isMethod && !isStaticMethod)
				{
					builder.append("self");
				}
				for(AbstractVariableData variableData : variableDatas)
				{
					if(variableData.passAsParameter)
					{
						if(builder.length() != 0)
						{
							builder.append(", ");
						}
						builder.append(variableData.name);
					}
				}
				builder.insert(0, "(");
				builder.insert(0, methodName);
				builder.insert(0, "def ");
				builder.append(")");
				return builder.toString();
			}
		};

		AbstractExtractMethodDialog dialog = new AbstractExtractMethodDialog(project, "method_name", fragment, validator, decorator, PythonFileType.INSTANCE)
		{
			@Override
			protected String getHelpId()
			{
				return "python.reference.extractMethod";
			}
		};
		dialog.show();

		//return if don`t want to extract method
		if(!dialog.isOK())
		{
			return Pair.empty();
		}

		return Pair.create(dialog.getMethodName(), dialog.getAbstractVariableData());
	}

	@Nonnull
	public static String getRefactoringId()
	{
		return "refactoring.python.extract.method";
	}

	private static class PyExtractMethodValidator implements ExtractMethodValidator
	{
		private final PsiElement myElement;
		private final Project myProject;
		@Nullable
		private final Function<String, Boolean> myFunction;

		public PyExtractMethodValidator(PsiElement element, Project project)
		{
			myElement = element;
			myProject = project;
			ScopeOwner parent = ScopeUtil.getScopeOwner(myElement);
			myFunction = s -> {
				ScopeOwner owner = parent;
				while(owner != null)
				{
					if(owner instanceof PyClass)
					{
						if(((PyClass) owner).findMethodByName(s, true, null) != null)
						{
							return false;
						}
					}
					Scope scope = ControlFlowCache.getScope(owner);
					if(scope.containsDeclaration(s))
					{
						return false;
					}
					owner = ScopeUtil.getScopeOwner(owner);
				}
				return true;
			};
		}

		@Nullable
		public String check(String name)
		{
			if(myFunction != null && !myFunction.apply(name))
			{
				return PyBundle.message("refactoring.extract.method.error.name.clash");
			}
			return null;
		}

		public boolean isValidName(@Nonnull String name)
		{
			NamesValidator validator = NamesValidator.forLanguage(PythonLanguage.getInstance());
			assert validator != null;
			return validator.isIdentifier(name, myProject);
		}
	}
}

