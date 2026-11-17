// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;


import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Converts {@code import foo} to {@code from foo import names} or {@code from ... import module} to {@code from ...module import names}.
 * Module names used as qualifiers are removed.
 * <br>
 * <i>NOTE: currently we only check usage of module name in the same file. For re-exported module names this is not sufficient.</i>
 * <br>
 */
// TODO consulo: ModCommand API (PsiBasedModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.ImportToImportFromIntention", categories = "Python", fileExtensions = "py")
public final class ImportToImportFromIntention extends PsiElementBaseIntentionAction {
    private static class IntentionState {
        private final PyImportElement myImportElement;
        private final @Nullable QualifiedName myQualifiedName;
        private final List<PyReferenceExpression> myReferences = new ArrayList<>();
        private final String myNewImportSource;
        // if anything that resolves to our imported module is just an exact reference to that module
        private boolean myHasModuleReference = false;

        IntentionState(PyImportElement importElement, @Nullable QualifiedName qualifiedName) {
            int relativeLevel = 0;
            boolean available = false;
            myImportElement = importElement;
            myQualifiedName = qualifiedName;
            PsiElement parent = myImportElement.getParent();
            if (parent instanceof PyImportStatement) {
                available = true;
            }
            else if (parent instanceof PyFromImportStatement fromImport) {
                relativeLevel = fromImport.getRelativeLevel();
                if (relativeLevel > 0 && fromImport.getImportSource() == null) {
                    available = true;
                }
            }
            PyReferenceExpression oldImportSource = myImportElement.getImportReferenceExpression();
            assert oldImportSource != null;
            myNewImportSource = StringUtil.repeat(".", relativeLevel) + PyPsiUtils.toPath(oldImportSource);
            if (available) {
                collectReferencesAndOtherData(oldImportSource); // this will cache data for the invocation
            }
        }

        public boolean isAvailable() {
            return !myReferences.isEmpty();
        }

        private void collectReferencesAndOtherData(PyReferenceExpression oldImportSource) {
            String qualifierName = getQualifierName(myImportElement);
            PsiElement referee = oldImportSource.getReference().resolve();
            myHasModuleReference = false;
            if (referee != null && qualifierName != null) {
                PsiTreeUtil.processElements(oldImportSource.getContainingFile(), new PsiElementProcessor<>() {
                    @Override
                    public boolean execute(PsiElement element) {
                        if (element instanceof PyReferenceExpression ref && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
                            if (qualifierName.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                                if (ref.getParent() instanceof PyReferenceExpression) { // really qualified by us, not just referencing?
                                        PsiElement resolved = ref.getReference().resolve();
                                    if (resolved == referee) {
                                        myReferences.add(ref);
                                    }
                                }
                                else {
                                    myHasModuleReference = true;
                                }
                            }
                        }
                        return true;
                    }
                });
            }
        }

        public boolean allReferencesHaveSameName() {
            return myReferences.size() == getSameNameReferences().size();
        }

        private Collection<PyReferenceExpression> getSameNameReferences() {
            if (myQualifiedName == null) {
                return myReferences;
            }
            return ContainerUtil.filter(myReferences,
                    ref -> ref.getParent() instanceof PyReferenceExpression parentRef &&
                    myQualifiedName.equals(parentRef.asQualifiedName()));
        }

        public void invoke(boolean unqualifyAll) {
            Project project = myImportElement.getProject();

            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            LanguageLevel languageLevel = LanguageLevel.forElement(myImportElement);

            // usages of imported name are qualifiers; what they refer to?
            try {
                // remember names and make them drop qualifiers
                Set<String> usedNames = new HashSet<>();
                Collection<PyReferenceExpression> referencesToUpdate = unqualifyAll ? myReferences : getSameNameReferences();
                for (PyReferenceExpression ref : referencesToUpdate) {
                    PsiElement parentElt = ref.getParent();
                    assert parentElt instanceof PyReferenceExpression : parentElt.getClass();
                    String nameUsed = Objects.requireNonNull(((PyReferenceExpression)parentElt).getReferencedName());
                    usedNames.add(nameUsed);
                    PyElement newReference = generator.createExpressionFromText(languageLevel, nameUsed);
                    parentElt.replace(newReference);
                }

                // create a separate import stmt for the module
                PsiElement importer = myImportElement.getParent();
                PyImportStatementBase importStatement;
                PyImportElement[] importElements;
                if (importer instanceof PyImportStatement qualifiedImportStatement) {
                    importStatement = qualifiedImportStatement;
                    importElements = importStatement.getImportElements();
                }
                else if (importer instanceof PyFromImportStatement fromImportStatement) {
                    importStatement = fromImportStatement;
                    importElements = importStatement.getImportElements();
                }
                else {
                    throw new IncorrectOperationException("Not an import at all");
                }
                PyFromImportStatement newImportStatement =
                        generator.createFromImportStatement(languageLevel, myNewImportSource, StringUtil.join(usedNames, ", "), null);
                PsiElement parent = importStatement.getParent();
                boolean canRemoveImport = !myHasModuleReference && referencesToUpdate.size() == myReferences.size();
                if (importElements.length == 1) {
                    if (!canRemoveImport) {
                        parent.addAfter(newImportStatement, importStatement); // add 'import from': we need the module imported as is
                    }
                    else { // replace entire existing import
                        importStatement.replace(newImportStatement);
                    }
                }
                else {
                    if (canRemoveImport) {
                        // cut the module out of import, add a from-import.
                        myImportElement.delete();
                    }
                    parent.addAfter(newImportStatement, importStatement);
                }
            }
            catch (IncorrectOperationException ignored) {
                PythonUiService.getInstance().showBalloonWarning(project, PyLocalize.qfixActionFailed().get());
            }
        }


        public LocalizeValue getText() {
            if (myQualifiedName == null) {
                return PyLocalize.intnConvertToFromImport(myNewImportSource, "...");
            }
            else {
                return PyLocalize.intnRemoveQualifier(getQualifierName(myImportElement));
            }
        }

        private static @Nullable String getQualifierName(PyImportElement importElement) {
            String asName = importElement.getAsName();
            if (asName != null) {
                return asName;
            }
            QualifiedName importedQName = importElement.getImportedQName();
            if (importedQName != null) {
                return importedQName.toString();
            }
            return null;
        }
    }

    private static @Nullable IntentionState createState(PyReferenceExpression refExprUnderCaret) {
        PyImportElement importElement = PsiTreeUtil.getParentOfType(refExprUnderCaret, PyImportElement.class);
        if (importElement != null) {
            return new IntentionState(importElement, null);
        }
        PyReferenceExpression reference =
                Objects.requireNonNullElse(PsiTreeUtil.getTopmostParentOfType(refExprUnderCaret, PyReferenceExpression.class), refExprUnderCaret);
        while (reference.getQualifier() instanceof PyReferenceExpression refQualifier) {
            for (ResolveResult rr : refQualifier.getReference().multiResolve(false)) {
                if (rr.isValidResult() &&
                        rr instanceof ImportedResolveResult irr &&
                        irr.getDefiner() instanceof PyImportElement importDefiner &&
                        importDefiner.getContainingFile() == refExprUnderCaret.getContainingFile()) {
                    return new IntentionState(importDefiner, reference.asQualifiedName());
                }
            }
            reference = refQualifier;
        }
        return null;
    }

    private final ThreeState myUnqualifyAll;

    @SuppressWarnings("unused")
    public ImportToImportFromIntention() {
        this(ThreeState.UNSURE);
    }

    private ImportToImportFromIntention(ThreeState unqualifyAll) {
        myUnqualifyAll = unqualifyAll;
        setText(PyLocalize.intnNameConvertImportUnqualify());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PyReferenceExpression element = PsiTreeUtil.getParentOfType(leaf, PyReferenceExpression.class, false);
        if (element == null) {
            return false;
        }
        LocalizeValue text = getPresentation(element);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    private @Nullable LocalizeValue getPresentation(PyReferenceExpression element) {
        if (!(element.getContainingFile() instanceof PyFile)) {
            return null;
        }

        IntentionState state = createState(element);
        if (state != null && state.isAvailable()) {
            return switch (myUnqualifyAll) {
                case YES -> PyLocalize.intnRemoveQualifierFromAllUsages();
                case NO -> PyLocalize.intnRemoveQualifierFromThisName();
                case UNSURE -> state.getText();
            };
        }
        return null;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyReferenceExpression element = PsiTreeUtil.getParentOfType(leaf, PyReferenceExpression.class, false);
        if (element == null) {
            return;
        }
        perform(element);
    }

    private void perform(PyReferenceExpression element) {
        switch (myUnqualifyAll) {
            case YES -> invoke(element, true);
            case NO -> invoke(element, false);
            case UNSURE -> {
                IntentionState state = createAndCheckState(element);
                if (state.allReferencesHaveSameName()) {
                    invoke(element, true);
                }
                else {
                    // TODO consulo: ModChooseAction missing - JB offers a choice between
                    // new ImportToImportFromIntention(ThreeState.YES) and new ImportToImportFromIntention(ThreeState.NO)
                    // titled PyLocalize.intnMultipleUsagesOfImportFound(); all usages are unqualified meanwhile
                    invoke(element, true);
                }
            }
        }
    }

    private static void invoke(PyReferenceExpression refExpr, boolean unqualifyAll) {
        // TODO consulo: ModCommand.psiUpdate missing - the physical element is updated directly
        createAndCheckState(refExpr).invoke(unqualifyAll);
    }

    private static IntentionState createAndCheckState(PyReferenceExpression refExpr) {
        IntentionState state = createState(refExpr);
        if (state == null || !state.isAvailable()) {
            throw new IllegalStateException();
        }
        return state;
    }
}
