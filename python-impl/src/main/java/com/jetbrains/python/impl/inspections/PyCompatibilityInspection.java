// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.google.common.collect.ImmutableList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.inspections.quickfix.WrapExceptTupleInParenthesesQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.validation.PyCompatibilityVisitor;
import com.jetbrains.python.impl.validation.UnsupportedFeaturesUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectRootManager;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: catherine
 * <p>
 * Inspection to detect code incompatibility with python versions
 */
@ExtensionImpl
public final class PyCompatibilityInspection extends PyInspection {

    public static final List<String> BACKPORTED_PACKAGES = ImmutableList.<String>builder()
        .add("enum")
        .add("typing")
        .add("dataclasses")
        .build();

    public static final List<String> COMPATIBILITY_LIBS = Collections.singletonList("six");

    public static final int LATEST_INSPECTION_VERSION = 3;

    public static final List<LanguageLevel> DEFAULT_PYTHON_VERSIONS =
        ImmutableList.of(LanguageLevel.PYTHON27, LanguageLevel.getLatest());

    public static final List<LanguageLevel> SUPPORTED_LEVELS = StreamEx
        .of(LanguageLevel.values())
        .filter(v -> v == LanguageLevel.PYTHON27 || v.isAtLeast(LanguageLevel.PYTHON37))
        .toImmutableList();

    private static final List<String> SUPPORTED_IN_SETTINGS = ContainerUtil.map(SUPPORTED_LEVELS, LanguageLevel::toString);

    public static @Nullable PyCompatibilityInspection getInstance(PsiElement element) {
        final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile();
        final String toolName = PyCompatibilityInspection.class.getSimpleName();
        return (PyCompatibilityInspection)inspectionProfile.getUnwrappedTool(toolName, element);
    }

    // Consulo: JB keeps the versions in the inspection field ourVersions (with OptPane options), Consulo keeps them in PyCompatibilityInspectionState
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyCompatibilityInspectionState();
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameCompatibility();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }


    private static List<LanguageLevel> updateVersionsToProcess(PyCompatibilityInspectionState state) {
        List<LanguageLevel> result = new ArrayList<>();

        for (String version : state.versions) {
            if (SUPPORTED_IN_SETTINGS.contains(version)) {
                LanguageLevel level = LanguageLevel.fromPythonVersion(version);
                result.add(level);
            }
        }
        return result;
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, updateVersionsToProcess((PyCompatibilityInspectionState)state));
    }

    private static class Visitor extends PyCompatibilityVisitor {
        private final ProblemsHolder myHolder;
        private final Set<String> myUsedImports = Collections.synchronizedSet(new HashSet<>());
        private final Set<String> myFromCompatibilityLibs = Collections.synchronizedSet(new HashSet<>());

        Visitor(ProblemsHolder holder, List<LanguageLevel> versionsToProcess) {
            super(versionsToProcess);
            myHolder = holder;
        }

        @Override
        protected void registerProblem(PsiElement element,
                                       TextRange range,
                                       String message,
                                       boolean asError,
                                       LocalQuickFix... fixes) {
            if (element.getTextLength() == 0) {
                return;
            }

            range = range.shiftRight(-element.getTextRange().getStartOffset());
            myHolder.registerProblem(element, range, message, fixes);
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            super.visitPyCallExpression(node);
            PyExpression callee = node.getCallee();
            if (callee != null && importedFromCompatibilityLibs(callee)) {
                return;
            }

            final TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());
            final PsiElement resolvedCallee = Optional
                .ofNullable(callee)
                .map(PyExpression::getReference)
                .map(PsiReference::resolve)
                .map(e -> e instanceof PyClass ? ((PyClass)e).findInitOrNew(false, context) : e)
                .orElse(null);

            if (resolvedCallee instanceof PyFunction function) {
                final PyClass containingClass = function.getContainingClass();

                final String functionName = PyUtil.isConstructorLikeMethod(function) ? callee.getText() : function.getName();

                if (containingClass != null) {
                    final String className = containingClass.getName();

                    if (UnsupportedFeaturesUtil.CLASS_METHODS.containsKey(className)) {
                        final Map<LanguageLevel, Set<String>> unsupportedMethods = UnsupportedFeaturesUtil.CLASS_METHODS.get(className);

                        registerForAllMatchingVersions(level -> unsupportedMethods.getOrDefault(level, Collections.emptySet()).contains(functionName),
                                                       PyLocalize.inspCompatibilityFeatureHaveMethod(functionName).get(),
                                                       node);
                    }
                }

                if (PyBuiltinCache.getInstance(function).isBuiltin(function) &&
                    !"print".equals(functionName) &&
                    !"exec".equals(functionName) &&
                    !myUsedImports.contains(functionName)) {
                    registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.BUILTINS.get(level).contains(functionName),
                                                   PyLocalize.inspCompatibilityFeatureHaveMethod(functionName).get(),
                                                   node);
                }
            }
            else if (resolvedCallee instanceof PyTargetExpression target) {

                if (!target.isQualified() &&
                    PyNames.TYPE_LONG.equals(target.getName()) &&
                    PyBuiltinCache.getInstance(resolvedCallee).isBuiltin(resolvedCallee)) {
                    registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.BUILTINS.get(level).contains(PyNames.TYPE_LONG),
                                                   PyLocalize.inspCompatibilityFeatureHaveTypeLong().get(),
                                                   node);
                }
            }
        }

        @Override
        public void visitPyImportElement(PyImportElement importElement) {
            myUsedImports.add(importElement.getVisibleName());

            final PyIfStatement ifParent = PsiTreeUtil.getParentOfType(importElement, PyIfStatement.class);
            if (ifParent != null) {
                return;
            }

            final PyTryExceptStatement tryExceptStatement = PsiTreeUtil.getParentOfType(importElement, PyTryExceptStatement.class);
            if (tryExceptStatement != null) {
                for (PyExceptPart part : tryExceptStatement.getExceptParts()) {
                    final PyExpression exceptClass = part.getExceptClass();
                    if (exceptClass != null && exceptClass.getText().equals("ImportError")) {
                        return;
                    }
                }
            }

            final QualifiedName qName = getImportedFullyQName(importElement);
            if (qName != null && !qName.matches("builtins") && !qName.matches("__builtin__")) {
                if (COMPATIBILITY_LIBS.contains(qName.getFirstComponent())) {
                    myFromCompatibilityLibs.add(qName.getLastComponent());
                }
                final String moduleName = qName.toString();

                registerForAllMatchingVersions(
                    level -> UnsupportedFeaturesUtil.MODULES.get(level).contains(moduleName) && !BACKPORTED_PACKAGES.contains(moduleName),
                    PyLocalize.inspCompatibilityFeatureHaveModule(moduleName).get(),
                    importElement);
            }
        }

        private static @Nullable QualifiedName getImportedFullyQName(PyImportElement importElement) {
            final QualifiedName importedQName = importElement.getImportedQName();
            if (importedQName == null) {
                return null;
            }

            final PyStatement containingImportStatement = importElement.getContainingImportStatement();
            final QualifiedName importSourceQName = containingImportStatement instanceof PyFromImportStatement
                                                    ? ((PyFromImportStatement)containingImportStatement).getImportSourceQName()
                                                    : null;

            return importSourceQName == null ? importedQName : importSourceQName.append(importedQName);
        }

        @Override
        public void visitPyFromImportStatement(PyFromImportStatement node) {
            super.visitPyFromImportStatement(node);

            if (node.getRelativeLevel() > 0) {
                return;
            }

            final QualifiedName name = node.getImportSourceQName();
            final PyReferenceExpression source = node.getImportSource();
            if (name != null && source != null) {
                final String moduleName = name.toString();

                registerForAllMatchingVersions(
                    level -> UnsupportedFeaturesUtil.MODULES.get(level).contains(moduleName) && !BACKPORTED_PACKAGES.contains(moduleName),
                    PyLocalize.inspCompatibilityFeatureHaveModule(name).get(),
                    source);
            }
        }

        @Override
        public void visitPyArgumentList(final PyArgumentList node) { //PY-5588
            if (node.getParent() instanceof PyClass) {
                final boolean isPython2 = LanguageLevel.forElement(node).isPython2();
                if (isPython2 || ContainerUtil.exists(myVersionsToProcess, LanguageLevel::isPython2)) {
                    Arrays
                        .stream(node.getArguments())
                        .filter(PyKeywordArgument.class::isInstance)
                        .forEach(expression -> myHolder.registerProblem(expression,
                                                                        PyLocalize.inspCompatibilityThisSyntaxAvailableOnlySincePy3().get(),
                                                                        !isPython2
                                                                        ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                                                        : ProblemHighlightType.GENERIC_ERROR));
                }
            }
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            super.visitPyElement(node);

            if (ContainerUtil.exists(myVersionsToProcess, LanguageLevel::isPy3K)) {
                final String nodeText = node.getText();

                if (nodeText.endsWith("iteritems") || nodeText.endsWith("iterkeys") || nodeText.endsWith("itervalues")) {
                    final PyExpression qualifier = node.getQualifier();
                    if (qualifier != null) {
                        final TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());
                        final PyType type = context.getType(qualifier);
                        final PyClassType dictType = PyBuiltinCache.getInstance(node).getDictType();
                        if (PyTypeChecker.match(dictType, type, context)) {
                            //noinspection DialogTitleCapitalization
                            registerProblem(node, PyLocalize.inspCompatibilityOldDictMethodsNotAvailableInPy3().get());
                        }
                    }
                }

                if (PyNames.BASESTRING.equals(nodeText)) {
                    final PsiElement res = node.getReference().resolve();
                    if (res != null) {
                        final PsiFile file = res.getContainingFile();
                        if (file != null) {
                            final VirtualFile virtualFile = file.getVirtualFile();
                            if (virtualFile != null && ProjectRootManager.getInstance(node.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
                                //noinspection DialogTitleCapitalization
                                registerProblem(node, PyLocalize.inspCompatibilityBasestringTypeNotAvailableInPy3().get());
                            }
                        }
                        else {
                            //noinspection DialogTitleCapitalization
                            registerProblem(node, PyLocalize.inspCompatibilityBasestringTypeNotAvailableInPy3().get());
                        }
                    }
                }
            }
        }

        @Override
        public void visitPyExceptBlock(PyExceptPart exceptExpr) {
            super.visitPyExceptBlock(exceptExpr);

            // TreeSet is used to sort versions
            TreeSet<LanguageLevel> allVersions = new TreeSet<>(myVersionsToProcess); // compatible-with versions
            allVersions.add(LanguageLevel.forElement(exceptExpr)); // dev version

            boolean hasBelow300 = ContainerUtil.exists(allVersions, level -> level.isOlderThan(LanguageLevel.PYTHON30));
            boolean hasBelow314 = ContainerUtil.exists(allVersions, level -> level.isOlderThan(LanguageLevel.PYTHON314));
            String allVersionsStr = StringUtil.join(allVersions, LanguageLevel::toString, ", ");

            PyExpression exceptClass = exceptExpr.getExceptClass();
            PyExpression target = exceptExpr.getTarget();
            if (exceptClass == null) {
                return;
            }

            if (LanguageLevel.forElement(exceptExpr).isAtLeast(LanguageLevel.PYTHON314)) { // see #StatementParsing.parseExceptPart()
                if (exceptClass instanceof PyTupleExpression tuple && tuple.getElements().length > 1) {
                    if (target != null) {
                        // see INSP.except.clause.missing.parens
                    }
                    else if (hasBelow300) {
                        // different semantics in versions <300 vs. >=314
                        registerProblem(exceptClass, PyLocalize.inspCompatibilityExceptClauseDifferentSemantics(allVersionsStr).get());
                    }
                    else if (hasBelow314) {
                        // we have: `except expr1, expr2`
                        // versions <314 require parentheses, versions >=314 not (only if the target is specified, but irrelevant here)
                        LocalQuickFix quickFix = new WrapExceptTupleInParenthesesQuickFix(tuple);
                        registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON30) && level.isOlderThan(LanguageLevel.PYTHON314),
                                                       PyLocalize.inspCompatibilityExceptClauseMissingParens().get(), exceptClass, quickFix);
                    }
                }
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            super.visitPyTargetExpression(node);
            warnAsyncAndAwaitAreBecomingKeywordsInPy37(node);
        }

        @Override
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);
            warnAsyncAndAwaitAreBecomingKeywordsInPy37(node);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);
            warnAsyncAndAwaitAreBecomingKeywordsInPy37(node);
        }

        @Override
        protected boolean registerForLanguageLevel(LanguageLevel level) {
            return level != LanguageLevel.forElement(myHolder.getFile());
        }

        private void warnAsyncAndAwaitAreBecomingKeywordsInPy37(PsiNameIdentifierOwner nameIdentifierOwner) {
            final PsiElement nameIdentifier = nameIdentifierOwner.getNameIdentifier();

            if (nameIdentifier != null &&
                ArrayUtil.contains(nameIdentifierOwner.getName(), PyNames.AWAIT, PyNames.ASYNC) &&
                LanguageLevel.forElement(nameIdentifierOwner).isOlderThan(LanguageLevel.PYTHON37)) {
                registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON37),
                                               PyLocalize.inspCompatibilityFeatureAllowAsyncAndAwaitAsNames().get(),
                                               nameIdentifier,
                                               PythonUiService.getInstance().createPyRenameElementQuickFix(nameIdentifierOwner));
            }
        }

        private boolean importedFromCompatibilityLibs(PyExpression callee) {
            if (callee instanceof PyQualifiedExpression) {
                QualifiedName qualifiedName = ((PyQualifiedExpression)callee).asQualifiedName();
                return qualifiedName != null && myFromCompatibilityLibs.contains(qualifiedName.getFirstComponent());
            }
            return myFromCompatibilityLibs.contains(callee.getName());
        }
    }
}
