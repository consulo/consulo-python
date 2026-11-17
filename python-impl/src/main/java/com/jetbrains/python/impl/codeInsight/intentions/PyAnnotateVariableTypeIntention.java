// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.impl.codeInsight.intentions.PyTypeHintGenerationUtil.Pep484IncompatibleTypeException;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyAnnotateVariableTypeIntention", categories = "Python", fileExtensions = "py")
public final class PyAnnotateVariableTypeIntention extends PyBaseIntentionAction {
    public PyAnnotateVariableTypeIntention() {
        setText(PyLocalize.intnNameAddTypeHintForVariable());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile) || psiFile instanceof PyDoctestFile) {
            return false;
        }
        List<PyTargetExpression> resolved = findSuitableTargetsUnderCaret(project, editor, psiFile);
        if (resolved.size() != 1) {
            return false;
        }

        setText(PyLocalize.intnAddTypeHintForVariable(resolved.get(0).getName()));
        return true;
    }

    private static List<PyTargetExpression> findSuitableTargetsUnderCaret(Project project, Editor editor, PsiFile file) {
        int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
        PyReferenceOwner elementAtCaret = PsiTreeUtil.getParentOfType(file.findElementAt(offset),
                PyReferenceExpression.class, PyTargetExpression.class);
        if (elementAtCaret == null) {
            return Collections.emptyList();
        }

        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(project, file);
        PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);
        // TODO filter out targets defined in stubs
        return StreamEx.of(resolveReferenceAugAssignmentsAware(elementAtCaret, resolveContext, new HashSet<>()))
            .select(PyTargetExpression.class)
            .filter(target -> {
                VirtualFile dir = target.getContainingFile().getOriginalFile().getVirtualFile();
                return dir != null && !index.isInLibraryClasses(dir);
            })
            .filter(target -> canBeAnnotated(target))
            .filter(target -> !isAnnotated(target, typeEvalContext))
            .toList();
    }

    private static StreamEx<PsiElement> resolveReferenceAugAssignmentsAware(PyReferenceOwner element,
            PyResolveContext resolveContext,
            Set<PyReferenceOwner> alreadyVisited) {
        alreadyVisited.add(element);
        return StreamEx.of(PyUtil.multiResolveTopPriority(element, resolveContext))
            .filter(resolved -> resolved instanceof PyTargetExpression || !alreadyVisited.contains(resolved))
            .flatMap(resolved -> expandResolveAugAssignments(resolved, resolveContext, alreadyVisited))
            .distinct();
    }

    private static StreamEx<PsiElement> expandResolveAugAssignments(PsiElement element,
            PyResolveContext context,
            Set<PyReferenceOwner> alreadyVisited) {
        if (element instanceof PyReferenceExpression && PyAugAssignmentStatementNavigator.getStatementByTarget(element) != null) {
            return StreamEx.of(resolveReferenceAugAssignmentsAware((PyReferenceOwner)element, context, alreadyVisited));
        }
        return StreamEx.of(element);
    }

    private static boolean canBeAnnotated(PyTargetExpression target) {
        PsiElement directParent = target.getParent();
        if (directParent instanceof PyImportElement ||
                directParent instanceof PyComprehensionForComponent ||
                directParent instanceof PyGlobalStatement ||
                directParent instanceof PyNonlocalStatement) {
            return false;
        }
        return PsiTreeUtil.getParentOfType(target, PyWithItem.class, PyAssignmentStatement.class, PyForPart.class) != null;
    }

    // TODO unify this logic with PyTypingTypeProvider somehow
    private static boolean isAnnotated(PyTargetExpression target, TypeEvalContext context) {
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
        String name = target.getName();
        if (scopeOwner == null || name == null) {
            return false;
        }

        if (!target.isQualified()) {
            if (hasInlineAnnotation(target)) {
                return true;
            }

            StreamEx<PyTargetExpression> candidates = null;
            if (context.maySwitchToAST(target)) {
                Scope scope = ControlFlowCache.getScope(scopeOwner);
                candidates = StreamEx.of(scope.getNamedElements(name, false)).select(PyTargetExpression.class);
            }
            // Unqualified target expression in either class or module
            else if (scopeOwner instanceof PyFile) {
                candidates = StreamEx.of(((PyFile)scopeOwner).getTopLevelAttributes()).filter(t -> name.equals(t.getName()));
            }
            else if (scopeOwner instanceof PyClass) {
                candidates = StreamEx.of(((PyClass)scopeOwner).getClassAttributes()).filter(t -> name.equals(t.getName()));
            }
            if (candidates != null) {
                return candidates.anyMatch(PyAnnotateVariableTypeIntention::hasInlineAnnotation);
            }
        }
        else if (isInstanceAttribute(target, context)) {
            // Set isDefinition=true to start searching right from the class level.
            List<PyTargetExpression> classLevelDefinitions = findClassLevelDefinitions(target, context);
            return ContainerUtil.exists(classLevelDefinitions, PyAnnotateVariableTypeIntention::hasInlineAnnotation);
        }
        return false;
    }

    private static List<PyTargetExpression> findClassLevelDefinitions(PyTargetExpression target,
            TypeEvalContext context) {
        assert target.getContainingClass() != null;
        assert target.getName() != null;
        PyClassTypeImpl classType = new PyClassTypeImpl(target.getContainingClass(), true);
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<? extends RatedResolveResult> classAttrs =
                classType.resolveMember(target.getName(), target, AccessDirection.READ, resolveContext, true);
        if (classAttrs == null) {
            return Collections.emptyList();
        }
        return StreamEx.of(classAttrs)
            .map(RatedResolveResult::getElement)
            .select(PyTargetExpression.class)
            .filter(x -> ScopeUtil.getScopeOwner(x) instanceof PyClass)
            .toList();
    }

    private static boolean isInstanceAttribute(PyTargetExpression target, TypeEvalContext context) {
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
        if (target.isQualified() && target.getContainingClass() != null && scopeOwner instanceof PyFunction) {

            if (context.maySwitchToAST(target)) {
                PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
                //noinspection ConstantConditions
                return StreamEx.of(PyUtil.multiResolveTopPriority(target.getQualifier(), resolveContext))
                    .select(PyParameter.class)
                    .filter(PyParameter::isSelf)
                    .anyMatch(p -> PsiTreeUtil.getParentOfType(p, PyFunction.class) == scopeOwner);
            }
            else {
                return PyUtil.isInstanceAttribute(target);
            }
        }
        return false;
    }

    private static boolean hasInlineAnnotation(PyTargetExpression target) {
        return target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        List<PyTargetExpression> targets = findSuitableTargetsUnderCaret(project, editor, file);
        assert targets.size() == 1;
        PyTargetExpression annotationTarget = targets.get(0);
        try {
            if (preferSyntacticAnnotation(annotationTarget)) {
                insertVariableAnnotation(annotationTarget);
            }
            else {
                insertVariableTypeComment(annotationTarget);
            }
        }
        catch (Pep484IncompatibleTypeException e) {
            PythonUiService.getInstance().showErrorHint(editor, e.getMessage());
        }
    }

    private static boolean preferSyntacticAnnotation(PyTargetExpression annotationTarget) {
        return LanguageLevel.forElement(annotationTarget).isAtLeast(LanguageLevel.PYTHON36);
    }

    private static void insertVariableAnnotation(PyTargetExpression target) {
        TypeEvalContext context = TypeEvalContext.userInitiated(target.getProject(), target.getContainingFile());
        PyType rawType = getInferredTypeOrObject(target, context);
        PyType inferredType = isInsideTupleUnpacking(target) ? rawType : PyLiteralType.upcastLiteralToClass(rawType);
        PyTypeHintGenerationUtil.checkPep484Compatibility(inferredType, context);
        String annotationText = PythonDocumentationProvider.getTypeHint(inferredType, context);
        String fqnTypeExr = PythonDocumentationProvider.getFullyQualifiedTypeHint(inferredType, context);
        AnnotationInfo info = new AnnotationInfo(annotationText, fqnTypeExr);
        if (isInstanceAttribute(target, context)) {
            List<PyTargetExpression> classLevelAttrs = findClassLevelDefinitions(target, context);
            if (classLevelAttrs.isEmpty()) {
                PyTypeHintGenerationUtil.insertStandaloneAttributeAnnotation(target, info, true);
            }
            else {
                PyTypeHintGenerationUtil.insertVariableAnnotation(classLevelAttrs.get(0), context, info, true);
            }
        }
        else {
            PyTypeHintGenerationUtil.insertVariableAnnotation(target, context, info, true);
        }
    }

    private static void insertVariableTypeComment(PyTargetExpression target) {
        TypeEvalContext context = TypeEvalContext.userInitiated(target.getProject(), target.getContainingFile());
        AnnotationInfo info = generateNestedTypeHint(target, context);
        if (isInstanceAttribute(target, context)) {
            List<PyTargetExpression> classLevelAttrs = findClassLevelDefinitions(target, context);
            if (classLevelAttrs.isEmpty()) {
                PyTypeHintGenerationUtil.insertStandaloneAttributeTypeComment(target, info, true);
            }
            else {
                // Use existing class level definition (say, assignment of the default value) for annotation
                PyTypeHintGenerationUtil.insertVariableTypeComment(classLevelAttrs.get(0), info, true);
            }
        }
        else {
            PyTypeHintGenerationUtil.insertVariableTypeComment(target, info, true);
        }
    }

    private static AnnotationInfo generateNestedTypeHint(PyTargetExpression target, TypeEvalContext context) {
        PyElement validTargetParent = PsiTreeUtil.getParentOfType(target, PyForPart.class, PyWithItem.class, PyAssignmentStatement.class);
        assert validTargetParent != null;
        PsiElement topmostTarget = PsiTreeUtil.findPrevParent(validTargetParent, target);
        StringBuilder builder = new StringBuilder();
        List<String> fullyQualifiedTypeHints = new ArrayList<>();
        List<TextRange> typeRanges = new ArrayList<>();
        generateNestedTypeHint(topmostTarget, true, context, builder, fullyQualifiedTypeHints, typeRanges);
        return new AnnotationInfo(builder.toString(), fullyQualifiedTypeHints, typeRanges);
    }

    private static void generateNestedTypeHint(PsiElement target,
            boolean widenLiterals,
            TypeEvalContext context,
            StringBuilder builder,
            List<String> fullyQualifiedTypeHints,
            List<TextRange> typeRanges) {
        if (target instanceof PyParenthesizedExpression) {
            PyExpression contained = ((PyParenthesizedExpression)target).getContainedExpression();
            if (contained != null) {
                generateNestedTypeHint(contained, widenLiterals, context, builder, fullyQualifiedTypeHints, typeRanges);
            }
        }
        else if (target instanceof PyTupleExpression) {
            builder.append("(");
            PyExpression[] elements = ((PyTupleExpression)target).getElements();
            for (int i = 0; i < elements.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                generateNestedTypeHint(elements[i], false, context, builder, fullyQualifiedTypeHints, typeRanges);
            }
            builder.append(")");
        }
        else if (target instanceof PyTypedElement) {
            PyType rawLeafType = getInferredTypeOrObject((PyTypedElement)target, context);
            PyType singleTargetType = widenLiterals ? PyLiteralType.upcastLiteralToClass(rawLeafType) : rawLeafType;
            PyTypeHintGenerationUtil.checkPep484Compatibility(singleTargetType, context);
            String singleTargetAnnotation = PythonDocumentationProvider.getTypeHint(singleTargetType, context);
            fullyQualifiedTypeHints.add(PythonDocumentationProvider.getFullyQualifiedTypeHint(singleTargetType, context));
            typeRanges.add(TextRange.from(builder.length(), singleTargetAnnotation.length()));
            builder.append(singleTargetAnnotation);
        }
    }

    private static @Nullable PyType getInferredTypeOrObject(PyTypedElement target, TypeEvalContext context) {
        PyType inferred = context.getType(target);
        return !isAnyOrUnknown(inferred) ? inferred : PyBuiltinCache.getInstance(target).getObjectType();
    }

    private static boolean isInsideTupleUnpacking(PyTargetExpression target) {
        PsiElement parent = PsiTreeUtil.skipParentsOfType(target, PyParenthesizedExpression.class);
        return parent instanceof PyTupleExpression;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}

