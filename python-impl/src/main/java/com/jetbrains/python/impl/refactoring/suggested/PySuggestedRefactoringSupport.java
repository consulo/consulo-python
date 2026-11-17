// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.refactoring.suggested;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringAvailability;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringExecution;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringState;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringStateChanges;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringSupport;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringUI;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public class PySuggestedRefactoringSupport implements SuggestedRefactoringSupport {

    public static final class Helper {
        private Helper() {
        }

        static boolean isAvailableForChangeSignature(PsiElement element) {
            if (!(element instanceof PyFunction function)) {
                return false;
            }
            String name = function.getName();
            return name != null && PyNames.isIdentifier(name) && function.getProperty() == null;
        }

        static @Nullable String defaultValue(SuggestedRefactoringSupport.Parameter parameter) {
            return parameter.getAdditionalData() instanceof ParameterData parameterData ? parameterData.defaultValue() : null;
        }

        static boolean isAvailableForRename(PsiElement element) {
            if (!(element instanceof PsiNameIdentifierOwner owner)) {
                return false;
            }
            String name = owner.getName();
            if (name == null || !PyNames.isIdentifier(name)) {
                return false;
            }
            if (!(element instanceof PyParameter parameter)) {
                return true;
            }
            PyFunction function = containingFunction(parameter);
            return function != null && !isAvailableForChangeSignature(function);
        }

        @RequiredReadAction
        static boolean shouldSuppressRefactoringForDeclaration(SuggestedRefactoringState state) {
            PsiElement element = state.restoredDeclarationCopy();
            PsiElement declaration = state.getDeclaration();
            return element == null ||
                PyiUtil.isOverload(element, TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile())) ||
                declaration != null && PyiUtil.isInsideStub(declaration) ||
                declaration instanceof PyElement pyElement && PyiUtil.getPythonStub(pyElement) != null;
        }

        private static @Nullable PyFunction containingFunction(PyParameter parameter) {
            return parameter.getParent() instanceof PyParameterList parameterList ? parameterList.getContainingFunction() : null;
        }
    }

    private final SuggestedRefactoringStateChanges myStateChanges = new PySuggestedRefactoringStateChanges(this);
    private final SuggestedRefactoringAvailability myAvailability = new PySuggestedRefactoringAvailability(this);
    private final SuggestedRefactoringExecution myExecution = new PySuggestedRefactoringExecution(this);

    @Override
    @RequiredReadAction
    public boolean isAnchor(PsiElement psiElement) {
        return findSupport(psiElement) != null;
    }

    @Override
    @RequiredReadAction
    public @Nullable TextRange signatureRange(PsiElement anchor) {
        SupportInternal support = findSupport(anchor);
        TextRange range = support != null ? support.signatureRange(anchor) : null;
        if (range == null || PsiTreeUtil.hasErrorElementInRange(anchor.getContainingFile(), range)) {
            return null;
        }
        return range;
    }

    @Override
    @RequiredReadAction
    public @Nullable TextRange importsRange(PsiFile psiFile) {
        return null;
    }

    @Override
    @RequiredReadAction
    public @Nullable TextRange nameRange(PsiElement anchor) {
        if (anchor instanceof PsiNameIdentifierOwner owner) {
            PsiElement nameIdentifier = owner.getNameIdentifier();
            return nameIdentifier != null ? nameIdentifier.getTextRange() : null;
        }
        return null;
    }

    @Override
    public boolean isIdentifierStart(char c) {
        return c == '_' || Character.isLetter(c);
    }

    @Override
    public boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isDigit(c);
    }

    @Override
    public SuggestedRefactoringStateChanges getStateChanges() {
        return myStateChanges;
    }

    @Override
    public SuggestedRefactoringAvailability getAvailability() {
        return myAvailability;
    }

    @Override
    public SuggestedRefactoringUI getUi() {
        return PySuggestedRefactoringUI.INSTANCE;
    }

    @Override
    public SuggestedRefactoringExecution getExecution() {
        return myExecution;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.getInstance();
    }

    record ParameterData(@Nullable String defaultValue) implements SuggestedRefactoringSupport.ParameterAdditionalData {
    }

    private @Nullable SupportInternal findSupport(PsiElement declaration) {
        for (SupportInternal support : List.of(ChangeSignatureSupport.INSTANCE, new RenameSupport(this))) {
            if (support.isApplicable(declaration)) {
                return support;
            }
        }
        return null;
    }

    private interface SupportInternal {
        boolean isApplicable(PsiElement element);

        @Nullable
        @RequiredReadAction
        TextRange signatureRange(PsiElement declaration);
    }

    private static final class ChangeSignatureSupport implements SupportInternal {
        static final ChangeSignatureSupport INSTANCE = new ChangeSignatureSupport();

        @Override
        public boolean isApplicable(PsiElement element) {
            return Helper.isAvailableForChangeSignature(element);
        }

        @Override
        @RequiredReadAction
        public @Nullable TextRange signatureRange(PsiElement declaration) {
            PyFunction function = (PyFunction) declaration;
            PsiElement name = function.getNameIdentifier();
            if (name == null) {
                return null;
            }
            ASTNode colon = function.getNode().findChildByType(PyTokenTypes.COLON);
            if (colon == null) {
                return null;
            }
            return TextRange.create(name.getTextRange().getStartOffset(), colon.getStartOffset());
        }
    }

    private static final class RenameSupport implements SupportInternal {
        private final PySuggestedRefactoringSupport myMainSupport;

        RenameSupport(PySuggestedRefactoringSupport mainSupport) {
            myMainSupport = mainSupport;
        }

        @Override
        public boolean isApplicable(PsiElement element) {
            return Helper.isAvailableForRename(element);
        }

        @Override
        @RequiredReadAction
        public @Nullable TextRange signatureRange(PsiElement declaration) {
            return myMainSupport.nameRange(declaration);
        }
    }
}
