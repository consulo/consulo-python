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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.NotificationType;
import consulo.ui.ex.InputValidator;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.codeInsight.intentions.DeclarationConflictChecker.findDefinitions;
import static com.jetbrains.python.impl.codeInsight.intentions.DeclarationConflictChecker.showConflicts;
import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Adds an alias to "import foo" or "from foo import bar" import elements, or removes it if it's already present.
 * User: dcheryasov
 * Date: Oct 9, 2009 6:07:19 PM
 */
public class ImportToggleAliasIntention extends PyBaseIntentionAction {
  private static class IntentionState {
    private PyImportElement myImportElement;
    private PyFromImportStatement myFromImportStatement;
    private PyImportStatement myImportStatement;
    private String myAlias;

    private static IntentionState fromContext(Editor editor, PsiFile file) {
      IntentionState state = new IntentionState();
      state.myImportElement = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyImportElement.class);
      PyPsiUtils.assertValid(state.myImportElement);
      if (state.myImportElement != null) {
        PyTargetExpression target = state.myImportElement.getAsNameElement();
        PyPsiUtils.assertValid(target);
        if (target != null) {
          state.myAlias = target.getName();
        }
        else {
          state.myAlias = null;
        }
        state.myFromImportStatement = PsiTreeUtil.getParentOfType(state.myImportElement, PyFromImportStatement.class);
        state.myImportStatement = PsiTreeUtil.getParentOfType(state.myImportElement, PyImportStatement.class);
      }
      return state;
    }

    public boolean isAvailable() {
      if (myFromImportStatement != null) {
        PyPsiUtils.assertValid(myFromImportStatement);
        if (!myFromImportStatement.isValid() || myFromImportStatement.isFromFuture()) {
          return false;
        }
      }
      else {
        PyPsiUtils.assertValid(myImportStatement);
        if (myImportStatement == null || !myImportStatement.isValid()) {
          return false;
        }
      }
      final PyReferenceExpression referenceExpression = myImportElement.getImportReferenceExpression();
      if (referenceExpression == null || referenceExpression.getReference().resolve() == null) {
        return false;
      }
      return true;
    }

    public String getText() {
      String add_name = "Add alias";
      if (myImportElement != null) {
        PyReferenceExpression refex = myImportElement.getImportReferenceExpression();
        if (refex != null) {
          add_name = PyBundle.message("INTN.add.alias.for.import.$0", refex.getText());
        }
      }
      return myAlias == null ? add_name : PyBundle.message("INTN.remove.alias.for.import.$0", myAlias);
    }
  }

  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.toggle.import.alias");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    IntentionState state = IntentionState.fromContext(editor, file);
    setText(state.getText());
    return state.isAvailable();
  }

  @Override
  public void doInvoke(@Nonnull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    // sanity check: isAvailable must have set it.
    final IntentionState state = IntentionState.fromContext(editor, file);
    //
    final String target_name; // we set in in the source
    final String remove_name; // we replace it in the source
    PyReferenceExpression reference = sure(state.myImportElement.getImportReferenceExpression());
    // search for references to us with the right name
    try {
      String imported_name = PyPsiUtils.toPath(reference);
      if (state.myAlias != null) {
        // have to remove alias, rename everything to original
        target_name = imported_name;
        remove_name = state.myAlias;
      }
      else {
        // ask for and add alias
        Application application = ApplicationManager.getApplication();
        if (application != null && !application.isUnitTestMode()) {
          String alias = Messages.showInputDialog(project,
                                                  PyBundle.message("INTN.alias.for.$0.dialog.title", imported_name),
                                                  "Add Alias",
                                                  Messages.getQuestionIcon(),
                                                  "",
                                                  new
                                                    InputValidator() {
                                                      @Override
                                                      public boolean checkInput(String inputString) {
                                                        return PyNames.isIdentifier(inputString);
                                                      }

                                                      @Override
                                                      public boolean canClose(String inputString) {
                                                        return PyNames.isIdentifier(inputString);
                                                      }
                                                    });
          if (alias == null) {
            return;
          }
          target_name = alias;
        }
        else { // test mode
          target_name = "alias";
        }
        remove_name = imported_name;
      }
      final PsiElement referee = reference.getReference().resolve();
      if (referee != null && imported_name != null) {
        final Collection<PsiReference> references = new ArrayList<>();
        final ScopeOwner scope = PsiTreeUtil.getParentOfType(state.myImportElement, ScopeOwner.class);
        PsiTreeUtil.processElements(scope, new PsiElementProcessor() {
          public boolean execute(@Nonnull PsiElement element) {
            getReferences(element);
            if (element instanceof PyStringLiteralExpression) {
              final PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)element;
              final List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host);
              if (files != null) {
                for (Pair<PsiElement, TextRange> pair : files) {
                  final PsiElement first = pair.getFirst();
                  if (first instanceof ScopeOwner) {
                    final ScopeOwner scopeOwner = (ScopeOwner)first;
                    PsiTreeUtil.processElements(scopeOwner, new PsiElementProcessor() {
                      public boolean execute(@Nonnull PsiElement element) {
                        getReferences(element);
                        return true;
                      }
                    });
                  }
                }
              }
            }
            return true;
          }

          private void getReferences(PsiElement element) {
            if (element instanceof PyReferenceExpression && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
              PyReferenceExpression ref = (PyReferenceExpression)element;
              if (remove_name.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                PsiElement resolved = ref.getReference().resolve();
                if (resolved == referee) {
                  references.add(ref.getReference());
                }
              }
            }
          }
        });
        // no references here is OK by us.
        if (showConflicts(project, findDefinitions(target_name, references, Collections.<PsiElement>emptySet()), target_name, null)) {
          return; // got conflicts
        }

        // alter the import element
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        final LanguageLevel languageLevel = LanguageLevel.forElement(state.myImportElement);
        if (state.myAlias != null) {
          // remove alias
          ASTNode node = sure(state.myImportElement.getNode());
          ASTNode parent = sure(node.getTreeParent());
          node = sure(node.getFirstChildNode()); // this is the reference
          node = sure(node.getTreeNext()); // things past the reference: space, 'as', and alias
          parent.removeRange(node, null);
        }
        else {
          // add alias
          ASTNode my_ielt_node = sure(state.myImportElement.getNode());
          PyImportElement fountain =
            generator.createFromText(languageLevel, PyImportElement.class, "import foo as " + target_name, new int[]{
              0,
              2
            });
          ASTNode graft_node = sure(fountain.getNode()); // at import elt
          graft_node = sure(graft_node.getFirstChildNode()); // at ref
          graft_node = sure(graft_node.getTreeNext()); // space
          my_ielt_node.addChild((ASTNode)graft_node.clone());
          graft_node = sure(graft_node.getTreeNext()); // 'as'
          my_ielt_node.addChild((ASTNode)graft_node.clone());
          graft_node = sure(graft_node.getTreeNext()); // space
          my_ielt_node.addChild((ASTNode)graft_node.clone());
          graft_node = sure(graft_node.getTreeNext()); // alias
          my_ielt_node.addChild((ASTNode)graft_node.clone());
        }
        // alter references
        for (PsiReference ref : references) {
          ASTNode ref_name_node = sure(sure(ref.getElement()).getNode());
          ASTNode parent = sure(ref_name_node.getTreeParent());
          ASTNode new_name_node = generator.createExpressionFromText(languageLevel, target_name).getNode();
          assert new_name_node != null;
          parent.replaceChild(ref_name_node, new_name_node);
        }
      }
    }
    catch (IncorrectOperationException ignored) {
      PyUtil.showBalloon(project, PyBundle.message("QFIX.action.failed"), NotificationType.WARNING);
    }
  }
}
