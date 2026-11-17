// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyClassPatternImpl;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAsPattern;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ExtensionImpl
public class PyPatternInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNamePatterns();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PyPatternInspectionVisitor(holder, context);
    }

    private static class PyPatternInspectionVisitor extends PyInspectionVisitor {
        private final ProblemsHolder holder;

        PyPatternInspectionVisitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
            this.holder = holder;
        }

        /**
         * Simplify `int() as name` to `int(name)`. Only for {@link PyClassPattern#SPECIAL_BUILTINS}.
         */
        @Override
        @RequiredReadAction
        public void visitPyAsPattern(PyAsPattern element) {
            PyPattern pattern = element.getPattern();
            if (element.getTarget() == null) {
                return;
            }

            if (pattern instanceof PyClassPattern classPattern &&
                isSpecialBuiltin(classPattern.getClassNameReference().getName()) &&
                classPattern.getArgumentList().getPatterns().isEmpty()) {
                holder.newProblem(PyLocalize.inspPatternsPatternCanBeSimplified())
                    .range(element)
                    .highlightType(ProblemHighlightType.WEAK_WARNING)
                    .withFix(new SimplifyAsPatternFix(element))
                    .create();
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyClassPattern(PyClassPattern node) {
            PyType type = myTypeEvalContext.getType(node.getClassNameReference());
            List<@Nullable PyType> types = PyTypeUtil.toStream(type).toList();
            if (!types.isEmpty() && types.stream().noneMatch(it -> PyTypeChecker.isUnknown(it, myTypeEvalContext))) {
                List<@Nullable PyType> invalidTypes = types.stream()
                    .filter(it -> !(it instanceof PyClassType classType) || !classType.isDefinition())
                    .collect(Collectors.toList());
                if (!invalidTypes.isEmpty()) {
                    PyType invalidTypesUnion = PyUnionType.unionOrUnknown(invalidTypes);
                    String invalidTypeName = PythonDocumentationProvider.getTypeName(invalidTypesUnion, myTypeEvalContext);
                    holder.newProblem(PyLocalize.inspPatternsNotAClass(node.getClassNameReference().getText(), invalidTypeName))
                        .range(node.getClassNameReference())
                        .create();
                    return;
                }
            }

            if (!(type instanceof PyClassType classType)) {
                return;
            }
            PyClass pyClass = classType.getPyClass();
            if (isSpecialBuiltin(pyClass.getName())) {
                return;
            }

            List<String> matchArgs = PyClassPatternImpl.getMatchArgs(classType, myTypeEvalContext);
            if (matchArgs == null) {
                for (PyPattern pattern : node.getArgumentList().getPatterns()) {
                    if (pattern instanceof PyKeywordPattern) {
                        continue;
                    }
                    holder.newProblem(PyLocalize.inspPatternsClassDoesNotSupportPatternMatchingWithPositionalArguments(
                            String.valueOf(pyClass.getName())))
                        .range(pattern)
                        .withFix(new AddMatchArgsFix(pyClass))
                        .create();
                }
                return;
            }

            List<PyPattern> positionalPatterns = new ArrayList<>();
            List<PyPattern> keywordPatterns = new ArrayList<>();
            for (PyPattern pattern : node.getArgumentList().getPatterns()) {
                if (!(pattern instanceof PyKeywordPattern)) {
                    positionalPatterns.add(pattern);
                }
                else {
                    keywordPatterns.add(pattern);
                }
            }

            for (int i = matchArgs.size(); i < positionalPatterns.size(); i++) {
                PyPattern pattern = positionalPatterns.get(i);
                holder.newProblem(PyLocalize.inspPatternsTooManyPositionalPatternsExpected(matchArgs.size()))
                    .range(pattern)
                    .withFix(new PyRemoveElementFix(pattern))
                    .create();
            }

            if (positionalPatterns.isEmpty() || keywordPatterns.isEmpty()) {
                return;
            }

            // Map positional patterns to their corresponding attribute names
            List<@Nullable String> positionalAttributeNames = new ArrayList<>();
            for (int index = 0; index < positionalPatterns.size(); index++) {
                positionalAttributeNames.add(index < matchArgs.size() ? matchArgs.get(index) : null);
            }

            // Check for conflicts between positional and named attributes
            for (PyPattern keywordPattern : keywordPatterns) {
                String keywordName = ((PyKeywordPattern) keywordPattern).getKeyword();
                int positionalIndex = positionalAttributeNames.indexOf(keywordName);
                if (positionalIndex >= 0) {
                    holder.newProblem(PyLocalize.inspPatternsAttributeAlreadySpecifiedAsPositionalPatternAtPosition(
                            keywordName, positionalIndex + 1))
                        .range(keywordPattern)
                        .withFix(new PyRemoveElementFix(keywordPattern))
                        .create();
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            PyTargetExpression matchArgsAttribute = node.findClassAttribute(PyNames.MATCH_ARGS, false, myTypeEvalContext);
            if (matchArgsAttribute == null) {
                return;
            }
            PyExpression assignedValue = matchArgsAttribute.findAssignedValue();
            if (assignedValue == null) {
                return;
            }
            PyExpression matchArgs = PyPsiUtils.flattenParens(assignedValue);
            if (matchArgs == null) {
                return;
            }

            PyType matchArgsType = myTypeEvalContext.getType(matchArgs);
            if (matchArgsType == null) {
                return;
            }
            PyClassType strType = PyBuiltinCache.getInstance(matchArgs).getStrType();
            if (strType == null) {
                return;
            }
            PyTupleType goodTuple = PyTupleType.createHomogeneous(matchArgs, strType);
            if (goodTuple == null) {
                return;
            }
            if (PyTypeChecker.match(goodTuple, matchArgsType, myTypeEvalContext)) {
                return;
            }
            // __match_args__ must be a tuple[str, ...]
            registerProblem(matchArgs,
                            PyInspectionMessages.problemMessage(
                                p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]),
                                PythonDocumentationProvider.getTypeName(goodTuple, myTypeEvalContext),
                                PythonDocumentationProvider.getTypeName(matchArgsType, myTypeEvalContext)));
        }

        private static boolean isSpecialBuiltin(@Nullable String name) {
            return name != null && PyClassPattern.SPECIAL_BUILTINS.contains(name);
        }
    }

    // JB: PsiUpdateModCommandAction (ModCommand API is not available in Consulo)
    private static class SimplifyAsPatternFix extends LocalQuickFixAndIntentionActionOnPsiElement {
        SimplifyAsPatternFix(PyAsPattern element) {
            super(element);
        }

        @Override
        public LocalizeValue getText() {
            return PyLocalize.qfixSimplifyAsPattern();
        }

        @Override
        public void invoke(Project project, PsiFile file, @Nullable Editor editor, PsiElement startElement, PsiElement endElement) {
            PyAsPattern element = (PyAsPattern) startElement;
            PyClassPattern pattern = (PyClassPattern) element.getPattern();
            PyTargetExpression target = element.getTarget();
            if (target == null) {
                return;
            }

            PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
            PyPattern newPattern = generator.createPatternFromText(
                LanguageLevel.forElement(element),
                pattern.getClassNameReference().getText() + "(" + target.getName() + ")"
            );

            element.replace(newPattern);
        }
    }

    // JB declares this class in PyPatternInspection.kt; ModCommand PsiUpdateModCommandAction is not available in Consulo
    public static class AddMatchArgsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
        private final @Nullable String myClassName;

        public AddMatchArgsFix(PyClass element) {
            super(element);
            myClassName = element.getName();
        }

        // JB getFamilyName(): PyPsiBundle.message("QFIX.NAME.add.match.args.to.class") -> PyLocalize.qfixNameAddMatchArgsToClass()
        @Override
        public LocalizeValue getText() {
            return PyLocalize.qfixAddMatchArgsToClass(String.valueOf(myClassName));
        }

        /**
         * Take positional arguments from `__init__`, check whether the class has an attribute with the same name,
         * and if so, add it to `__match_args__`.
         */
        @Override
        public void invoke(Project project, PsiFile file, @Nullable Editor editor, PsiElement startElement, PsiElement endElement) {
            PyClass pyClass = (PyClass) startElement;
            TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(pyClass.getProject(), pyClass.getContainingFile());

            PyFunction initMethod = pyClass.findMethodByName(PyNames.INIT, false, typeEvalContext);
            List<String> positionalArgs = new ArrayList<>();
            if (initMethod != null) {
                PyParameter[] parameters = initMethod.getParameterList().getParameters();
                for (int i = 1; i < parameters.length; i++) {
                    String name = parameters[i].getName();
                    if (name != null && pyClass.findInstanceAttribute(name, true) != null) {
                        positionalArgs.add(name);
                    }
                }
            }

            PyElementGenerator generator = PyElementGenerator.getInstance(pyClass.getProject());
            String matchArgsValue = positionalArgs.stream()
                .map(it -> "'" + it + "'")
                .collect(Collectors.joining(", ", "(", positionalArgs.size() != 1 ? ")" : ",)"));
            PyAssignmentStatement matchArgsAssignment = generator.createFromText(
                LanguageLevel.forElement(pyClass),
                PyAssignmentStatement.class,
                PyNames.MATCH_ARGS + " = " + matchArgsValue
            );

            PyStatementList statementList = pyClass.getStatementList();
            PyStatement[] statements = statementList.getStatements();
            PyStatement anchor = statements.length > 0 ? statements[0] : null;
            PyAssignmentStatement result = (PyAssignmentStatement) statementList.addBefore(matchArgsAssignment, anchor);
            if (editor != null) {
                editor.getCaretModel().moveToOffset(result.getTextRange().getEndOffset() - 1);
            }
            PyPsiUtils.removeRedundantPass(statementList);
        }
    }
}
