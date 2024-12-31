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
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.codeEditor.Editor;
import consulo.language.editor.AutoImportHelper;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.psi.*;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * The object contains a list of import candidates and serves only to show the initial hint;
 * the actual work is done in ImportFromExistingAction..
 *
 * @author dcheryasov
 */
public class AutoImportQuickFix extends LocalQuickFixOnPsiElement implements HighPriorityAction {

  private final List<ImportCandidateHolder> myImports; // from where and what to import
  private final String myInitialName;
  private final boolean myUseQualifiedImport;
  private final Class<? extends PsiReference> myReferenceType;
  private boolean myExpended = false;

  /**
   * Creates a new, empty fix object.
   *
   * @param node          to which the fix applies.
   * @param referenceType
   * @param name          name to import
   * @param qualify       if true, add an "import ..." statement and qualify the name; else use "from ... import name"
   */
  public AutoImportQuickFix(@Nonnull PsiElement node,
                            @Nonnull Class<? extends PsiReference> referenceType,
                            @Nonnull String name,
                            boolean qualify) {
    this(node, referenceType, name, qualify, Collections.emptyList());
  }

  private AutoImportQuickFix(@Nonnull PsiElement node,
                             @Nonnull Class<? extends PsiReference> referenceType,
                             @Nonnull String name,
                             boolean qualify,
                             @Nonnull Collection<ImportCandidateHolder> candidates) {
    super(node);
    myReferenceType = referenceType;
    myInitialName = name;
    myUseQualifiedImport = qualify;
    myImports = new ArrayList<>(candidates);
  }

  /**
   * Adds another import source.
   *
   * @param importable    an element that could be imported either from import element or from file.
   * @param file          the file which is the source of the importable
   * @param importElement an existing import element that can be a source for the importable.
   */
  public void addImport(@Nonnull PsiElement importable, @Nonnull PsiFile file, @Nullable PyImportElement importElement) {
    myImports.add(new ImportCandidateHolder(importable, file, importElement, null));
  }

  /**
   * Adds another import source.
   *
   * @param importable an element that could be imported either from import element or from file.
   * @param file       the file which is the source of the importable
   * @param path       import path for the file, as a qualified name (a.b.c)
   */
  public void addImport(@Nonnull PsiElement importable, @Nonnull PsiFileSystemItem file, @Nullable QualifiedName path) {
    myImports.add(new ImportCandidateHolder(importable, file, null, path));
  }

  public void addImport(@Nonnull PsiElement importable,
                        @Nonnull PsiFileSystemItem file,
                        @Nullable QualifiedName path,
                        @Nullable String asName) {
    myImports.add(new ImportCandidateHolder(importable, file, null, path, asName));
  }

  @Nonnull
  public String getText() {
    if (myUseQualifiedImport) {
      return PyBundle.message("ACT.qualify.with.module");
    }
    else if (myImports.size() == 1) {
      return PyBundle.message("QFIX.auto.import.import.name", myImports.get(0).getPresentableText(myInitialName));
    }
    else {
      return PyBundle.message("QFIX.auto.import.import.this.name");
    }
  }

  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("QFIX.auto.import.family");
  }

  public boolean showHint(Editor editor) {
    if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP ||
      HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true) ||
      myImports.isEmpty()) {
      return false;
    }
    final PsiElement element = getStartElement();
    PyPsiUtils.assertValid(element);
    if (element == null || !element.isValid()) {
      return false;
    }
    final PyElement pyElement = as(element, PyElement.class);
    if (pyElement == null || !myInitialName.equals(pyElement.getName())) {
      return false;
    }
    final PsiReference reference = findOriginalReference(element);
    if (reference == null || isResolved(reference)) {
      return false;
    }
    if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).isQualified()) {
      return false; // we cannot be qualified
    }

    final String message = AutoImportHelper.getInstance(element.getProject()).getImportMessage(myImports.size() > 1,
                                                                                               ImportCandidateHolder.getQualifiedName(
                                                                                                 myInitialName,
                                                                                                 myImports.get(0).getPath(),
                                                                                                 myImports.get(0).getImportElement
                                                                                                   ()));
    final ImportFromExistingAction action = new ImportFromExistingAction(element, myImports, myInitialName, myUseQualifiedImport, false);
    action.onDone(() -> myExpended = true);
    HintManager.getInstance().showQuestionHint(editor, message, element.getTextOffset(), element.getTextRange().getEndOffset(), action);
    return true;
  }

  public boolean isAvailable() {
    final PsiElement element = getStartElement();
    if (element == null) {
      return false;
    }
    PyPsiUtils.assertValid(element);
    return !myExpended && element.isValid() && !myImports.isEmpty();
  }

  @Override
  public void invoke(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement) {
    invoke(getStartElement().getContainingFile());
  }

  public void invoke(PsiFile file) throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    final PsiElement startElement = getStartElement();
    if (startElement == null) {
      return;
    }
    PyPsiUtils.assertValid(startElement);
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }
    final PsiReference reference = findOriginalReference(startElement);
    if (reference == null || isResolved(reference)) {
      return;
    }
    // act
    ImportFromExistingAction action = createAction();
    action.execute(); // assume that action runs in WriteAction on its own behalf
    myExpended = true;
  }

  @Nonnull
  protected ImportFromExistingAction createAction() {
    return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, myUseQualifiedImport, false);
  }

  public void sortCandidates() {
    Collections.sort(myImports);
  }

  @Nonnull
  public List<ImportCandidateHolder> getCandidates() {
    return Collections.unmodifiableList(myImports);
  }

  public boolean hasOnlyFunctions() {
    for (ImportCandidateHolder holder : myImports) {
      if (!(holder.getImportable() instanceof PyFunction)) {
        return false;
      }
    }
    return true;
  }

  public boolean hasProjectImports() {
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(getStartElement().getProject());
    for (ImportCandidateHolder anImport : myImports) {
      VirtualFile file = anImport.getFile().getVirtualFile();
      if (file != null && fileIndex.isInContent(file)) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  public AutoImportQuickFix forLocalImport() {
    return new AutoImportQuickFix(getStartElement(), myReferenceType, myInitialName, myUseQualifiedImport, myImports) {
      @Nonnull
      @Override
      public String getFamilyName() {
        return PyBundle.message("QFIX.local.auto.import.family");
      }

      @Nonnull
      @Override
      public String getText() {
        return PyBundle.message("QFIX.local.auto.import.import.locally", super.getText());
      }

      @Nonnull
      @Override
      protected ImportFromExistingAction createAction() {
        return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, myUseQualifiedImport, true);
      }
    };
  }

  @Nonnull
  public String getNameToImport() {
    return myInitialName;
  }

  private static boolean isResolved(@Nonnull PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      return ((PsiPolyVariantReference)reference).multiResolve(false).length > 0;
    }
    return reference.resolve() != null;
  }

  @Nullable
  private PsiReference findOriginalReference(@Nonnull PsiElement element) {
    return ContainerUtil.findInstance(element.getReferences(), myReferenceType);
  }
}
