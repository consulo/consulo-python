// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.types.PyIntersectionType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.Application;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.Lists;
import consulo.util.lang.function.ThrowableRunnable;
import consulo.util.lang.function.ThrowableSupplier;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;

/**
 * @author Mikhail Golubev
 */
public final class PyTypeHintGenerationUtil {

    public static final String TYPE_COMMENT_PREFIX = "# type: ";

    private PyTypeHintGenerationUtil() {
    }

    public static void insertStandaloneAttributeTypeComment(PyTargetExpression target,
                                                            AnnotationInfo info,
                                                            boolean startTemplate) {

        PyClass pyClass = target.getContainingClass();
        if (pyClass == null) {
            throw new IllegalArgumentException("Target '" + target.getText() + "' in not contained in a class definition");
        }

        if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) {
            return;
        }

        PyElementGenerator generator = PyElementGenerator.getInstance(target.getProject());
        LanguageLevel langLevel = LanguageLevel.forElement(target);
        String assignedValue = langLevel.isPython2() ? "None" : "...";
        String declarationText = target.getName() + " = " + assignedValue + " " + TYPE_COMMENT_PREFIX + info.getAnnotationText();
        PyAssignmentStatement declaration = generator.createFromText(langLevel, PyAssignmentStatement.class, declarationText);
        PsiElement anchorBefore = findPrecedingAnchorForAttributeDeclaration(pyClass);

        WriteAction.run(() -> {
            PyAssignmentStatement inserted = (PyAssignmentStatement) pyClass.getStatementList().addAfter(declaration, anchorBefore);
            PsiComment insertedComment = as(inserted.getLastChild(), PsiComment.class);
            if (insertedComment == null) {
                return;
            }

            addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

            insertedComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedComment);
            if (startTemplate && insertedComment != null) {
                openEditorAndAddTemplateForTypeComment(insertedComment, info.getAnnotationText(), info.getTypeRanges());
            }
        });
    }

    public static void insertStandaloneAttributeAnnotation(PyTargetExpression target,
                                                           AnnotationInfo info,
                                                           boolean startTemplate) {
        LanguageLevel langLevel = LanguageLevel.forElement(target);
        if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
            throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
        }

        PyClass pyClass = target.getContainingClass();
        if (pyClass == null) {
            throw new IllegalArgumentException("Target '" + target.getText() + "' in not contained in a class definition");
        }

        if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) {
            return;
        }

        PyElementGenerator generator = PyElementGenerator.getInstance(target.getProject());
        String declarationText = target.getName() + ": " + info.getAnnotationText();
        PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
        PsiElement anchorBefore = findPrecedingAnchorForAttributeDeclaration(pyClass);

        WriteAction.run(() -> {
            PyTypeDeclarationStatement inserted = (PyTypeDeclarationStatement) pyClass.getStatementList().addAfter(declaration, anchorBefore);

            addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

            inserted = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(inserted);
            if (startTemplate && inserted != null) {
                openEditorAndAddTemplateForAnnotation(inserted);
            }
        });
    }

    private static @Nullable PsiElement findPrecedingAnchorForAttributeDeclaration(PyClass pyClass) {
        PyStatement firstStatement = pyClass.getStatementList().getStatements()[0];
        PyStringLiteralExpression classDocstring = pyClass.getDocStringExpression();
        if (firstStatement instanceof PyExpressionStatement && classDocstring == ((PyExpressionStatement) firstStatement).getExpression()) {
            return firstStatement;
        }
        return null;
    }

    public static void insertVariableAnnotation(PyTargetExpression target,
                                                @Nullable TypeEvalContext context,
                                                AnnotationInfo info,
                                                boolean startTemplate) {
        LanguageLevel langLevel = LanguageLevel.forElement(target);
        if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
            throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
        }

        if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) {
            return;
        }

        Project project = target.getProject();
        ThrowableSupplier<@Nullable PyAnnotationOwner, RuntimeException> addOrUpdateAnnotatedStatement;
        if (canUseInlineAnnotation(target)) {
            SmartPointerManager manager = SmartPointerManager.getInstance(project);
            PyAssignmentStatement assignment = (PyAssignmentStatement) target.getParent();
            SmartPsiElementPointer<PyAssignmentStatement> pointer = manager.createSmartPsiElementPointer(assignment);
            addOrUpdateAnnotatedStatement = () -> {
                PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
                    document.insertString(target.getTextRange().getEndOffset(), ": " + info.getAnnotationText());
                });
                return pointer.getElement();
            };
        }
        else {
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            String declarationText = target.getName() + ": " + info.getAnnotationText();
            PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
            PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
            assert statement != null;
            addOrUpdateAnnotatedStatement = () -> (PyAnnotationOwner) statement.getParent().addBefore(declaration, statement);
        }

        WriteAction.run(() -> {
            PyAnnotationOwner createdAnnotationOwner = addOrUpdateAnnotatedStatement.get();
            if (createdAnnotationOwner == null) {
                return;
            }

            if (context != null) {
                addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);
            }

            createdAnnotationOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(createdAnnotationOwner);
            if (startTemplate && createdAnnotationOwner != null) {
                openEditorAndAddTemplateForAnnotation(createdAnnotationOwner);
            }
        });
    }

    private static void openEditorAndAddTemplateForAnnotation(PyAnnotationOwner annotated) {
        assert annotated.isValid();
        assert annotated.getAnnotationValue() != null;

        Project project = annotated.getProject();
        int initialCaretOffset = annotated.getTextRange().getStartOffset();
        VirtualFile updatedVirtualFile = annotated.getContainingFile().getVirtualFile();
        TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotated);
        String annotation = annotated.getAnnotationValue();
        String replacementText = Application.get().isUnitTestMode() ? "[" + annotation + "]" : annotation;
        //noinspection ConstantConditions
        templateBuilder.replaceElement(annotated.getAnnotation().getValue(), replacementText);

        Editor editor = PythonUiService.getInstance().openTextEditor(project, updatedVirtualFile, initialCaretOffset);
        if (editor != null) {
            editor.getCaretModel().moveToOffset(initialCaretOffset);
            templateBuilder.run(editor, true);
        }
        else {
            // TODO consulo: TemplateBuilder.runNonInteractively missing - JB: templateBuilder.runNonInteractively(true);
        }
    }

    private static boolean canUseInlineAnnotation(PyTargetExpression target) {
        PyAssignmentStatement assignment = as(target.getParent(), PyAssignmentStatement.class);
        return assignment != null && assignment.getRawTargets().length == 1 && assignment.getLeftHandSideExpression() == target;
    }

    public static void insertVariableTypeComment(PyTargetExpression target,
                                                 AnnotationInfo info,
                                                 boolean startTemplate) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) {
            return;
        }

        String typeCommentText = "  " + TYPE_COMMENT_PREFIX + info.getAnnotationText();

        PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
        PsiElement insertionAnchor;
        if (statement instanceof PyAssignmentStatement) {
            insertionAnchor = statement.getLastChild();
        }
        else if (statement instanceof PyWithStatement) {
            insertionAnchor = PyUtilCore.getHeaderEndAnchor((PyStatementListContainer) statement);
        }
        else if (statement instanceof PyForStatement) {
            insertionAnchor = PyUtilCore.getHeaderEndAnchor(((PyForStatement) statement).getForPart());
        }
        else {
            throw new IllegalArgumentException("Target expression must belong to an assignment, \"with\" statement or \"for\" loop");
        }

        ThrowableRunnable<RuntimeException> insertComment;
        if (insertionAnchor instanceof PsiComment) {
            String combinedTypeCommentText = typeCommentText + " " + insertionAnchor.getText();
            PsiElement lastNonComment = PyPsiUtils.getPrevNonCommentSibling(insertionAnchor, true);
            int startOffset = lastNonComment.getTextRange().getEndOffset();
            int endOffset = insertionAnchor.getTextRange().getEndOffset();
            insertComment = () -> PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
                document.replaceString(startOffset, endOffset, combinedTypeCommentText);
            });
        }
        else if (insertionAnchor != null) {
            int offset = insertionAnchor.getTextRange().getEndOffset();
            insertComment = () -> PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
                document.insertString(offset, typeCommentText);
            });
        }
        else {
            return;
        }

        WriteAction.run(() -> {
            insertComment.run();
            PsiComment insertedComment = target.getTypeComment();
            if (insertedComment == null) {
                return;
            }

            addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

            insertedComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedComment);
            if (startTemplate && insertedComment != null) {
                openEditorAndAddTemplateForTypeComment(insertedComment, info.getAnnotationText(), info.getTypeRanges());
            }
        });
    }

    private static void openEditorAndAddTemplateForTypeComment(PsiComment insertedComment,
                                                               String annotation,
                                                               List<TextRange> typeRanges) {
        int initialCaretOffset = insertedComment.getTextRange().getStartOffset();
        VirtualFile updatedVirtualFile = insertedComment.getContainingFile().getVirtualFile();
        Project project = insertedComment.getProject();
        TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(insertedComment);
        boolean testMode = Application.get().isUnitTestMode();
        for (TextRange range : typeRanges) {
            String individualType = range.substring(annotation);
            String replacementText = testMode ? "[" + individualType + "]" : individualType;
            templateBuilder.replaceRange(range.shiftRight(TYPE_COMMENT_PREFIX.length()), replacementText);
        }

        Editor editor = PythonUiService.getInstance().openTextEditor(project, updatedVirtualFile, initialCaretOffset);
        if (editor != null) {
            editor.getCaretModel().moveToOffset(initialCaretOffset);
            templateBuilder.run(editor, true);
        }
        else {
            // TODO consulo: TemplateBuilder.runNonInteractively missing - JB: templateBuilder.runNonInteractively(true);
        }
    }

    /**
     * Adds imports for type annotations. Sorts imports by name.
     */
    public static void addImportsForTypeAnnotations(Collection<String> types, PsiElement anchor) {
        Set<PsiNamedElement> symbols =
            new TreeSet<>(Comparator.comparing(PsiNamedElement::getName, Comparator.nullsFirst(Comparator.naturalOrder())));

        for (String type : types) {
            collectImportTargetsFromTypeExpression(type, anchor, symbols);
        }

        PsiFile file = anchor.getContainingFile();
        for (PsiNamedElement symbol : symbols) {
            PyPsiRefactoringUtil.insertImport(file, symbol, null, true);
        }
    }

    private static void collectImportTargetsFromTypeExpression(String typeExpressionText,
                                                               PsiElement anchor,
                                                               Set<PsiNamedElement> symbols) {
        PyExpression typeExpression = PyUtil.createExpressionFromFragment(typeExpressionText, anchor);
        assert typeExpression != null;
        PyQualifiedNameResolveContext qNameResolveContext = PyResolveImportUtil.fromFoothold(anchor);
        typeExpression.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                if (node.isQualified()) {
                    QualifiedName qualifiedName = node.asQualifiedName();
                    if (qualifiedName != null) {
                        PsiElement element = PyResolveImportUtil.resolveTopLevelMember(qualifiedName, qNameResolveContext);
                        if (element instanceof PsiNamedElement namedElement) {
                            symbols.add(namedElement);
                            return;
                        }
                    }
                }
                super.visitPyReferenceExpression(node);
            }
        });
    }

    public static void checkPep484Compatibility(@Nullable PyType type, TypeEvalContext context) {
        if (isAnyOrUnknown(type) ||
            isNoneType(type) ||
            // Will be rendered as just Any
            type instanceof PyUnsafeUnionType ||
            type instanceof PyIntersectionType ||
            type instanceof PyTypeParameterType) {
            return;
        }
        else if (type instanceof PyUnionType unionType) {
            for (PyType memberType : unionType.getMembers()) {
                checkPep484Compatibility(memberType, context);
            }
        }
        else if (type instanceof PyClassType pyClassType) {
            for (PyType typeParam : pyClassType.getTypeArguments()) {
                checkPep484Compatibility(typeParam, context);
            }
        }
        else if (type instanceof PyCallableType callableType) {
            for (PyCallableParameter parameter : Lists.notNullize(callableType.getParameters(context))) {
                checkPep484Compatibility(parameter.getType(context), context);
            }
            checkPep484Compatibility(callableType.getReturnType(context), context);
        }
        else {
            throw new Pep484IncompatibleTypeException(
                PyLocalize.intnAddTypeHintForVariablePep484IncompatibleType(type.getName()).get());
        }
    }

    public static boolean isTypeHintComment(PsiElement element) {
        return element instanceof PsiComment && element.getText().startsWith(TYPE_COMMENT_PREFIX);
    }

    public static final class Pep484IncompatibleTypeException extends RuntimeException {
        public Pep484IncompatibleTypeException(String message) {
            super(message);
        }
    }

    public static final class AnnotationInfo {
        private final String myAnnotationText;
        private final List<String> myFullyQualifiedTypeHints;
        private final List<TextRange> myTypeRanges;

        public AnnotationInfo(String annotationText) {
            this(annotationText, Collections.emptyList(), Collections.singletonList(TextRange.allOf(annotationText)));
        }

        public AnnotationInfo(String annotationText, String fullyQualifiedTypeHint) {
            this(annotationText, Collections.singletonList(fullyQualifiedTypeHint), Collections.singletonList(TextRange.allOf(annotationText)));
        }

        public AnnotationInfo(String annotationText,
                              List<String> fullyQualifiedTypeHints,
                              List<TextRange> typeRanges) {
            myAnnotationText = annotationText;
            myFullyQualifiedTypeHints = fullyQualifiedTypeHints;
            myTypeRanges = typeRanges;
        }

        public String getAnnotationText() {
            return myAnnotationText;
        }

        public List<String> getFullyQualifiedTypeHints() {
            return myFullyQualifiedTypeHints;
        }

        public List<TextRange> getTypeRanges() {
            return myTypeRanges;
        }
    }
}
