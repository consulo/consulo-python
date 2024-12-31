
/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * User : ktisha
 * <p>
 * Quick fix to rename unresolved references
 */
public class PyRenameUnresolvedRefQuickFix implements LocalQuickFix {

  @Nonnull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.rename.unresolved.reference");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("QFIX.rename.unresolved.reference");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
    if (referenceExpression == null) return;

    ScopeOwner parentScope = ScopeUtil.getScopeOwner(referenceExpression);
    if (parentScope == null) return;

    List<PyReferenceExpression> refs = collectExpressionsToRename(referenceExpression, parentScope);

    LookupElement[] items = collectLookupItems(parentScope);
    final String name = referenceExpression.getReferencedName();

    ReferenceNameExpression refExpr = new ReferenceNameExpression(items, name);
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().
      createTemplateBuilder(parentScope);
    for (PyReferenceExpression expr : refs) {
      if (!expr.equals(referenceExpression)) {
        builder.replaceElement(expr, name, name, false);
      }
      else {
        builder.replaceElement(expr, name, refExpr, true);
      }
    }

    Editor editor = getEditor(project, element.getContainingFile(), parentScope.getTextRange().getStartOffset());
    if (editor != null) {
      Template template = builder.buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
  }

  public static boolean isValidReference(final PsiReference reference) {
    if (!(reference instanceof PyReferenceImpl)) return false;
    ResolveResult[] results = ((PyReferenceImpl)reference).multiResolve(true);
    if (results.length == 0) return false;
    for (ResolveResult result : results) {
      if (!result.isValidResult()) return false;
    }
    return true;
  }


  private static List<PyReferenceExpression> collectExpressionsToRename(@Nonnull final PyReferenceExpression expression,
                                                                        @Nonnull final ScopeOwner parentScope) {

    final List<PyReferenceExpression> result = new ArrayList<PyReferenceExpression>();
    PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        if (node.textMatches(expression) && !isValidReference(node.getReference())) {
          result.add(node);
        }
        super.visitPyReferenceExpression(node);
      }
    };

    parentScope.accept(visitor);
    return result;
  }

  @Nullable
  private static Editor getEditor(@Nonnull final Project project, @Nonnull final PsiFile file, int offset) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      return FileEditorManager.getInstance(project).openTextEditor(
        OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).offset(offset).build(), true
      );
    }
    return null;
  }

  private static LookupElement[] collectLookupItems(@Nonnull final ScopeOwner parentScope) {
    Set<LookupElement> items = new LinkedHashSet<LookupElement>();

    final Collection<String> usedNames = PyRefactoringUtil.collectUsedNames(parentScope);
    for (String name : usedNames) {
      if (name != null)
        items.add(LookupElementBuilder.create(name));
    }

    return items.toArray(new LookupElement[items.size()]);
  }

  private class ReferenceNameExpression extends Expression {
    class HammingComparator implements Comparator<LookupElement> {
      @Override
      public int compare(LookupElement lookupItem1, LookupElement lookupItem2) {
        String s1 = lookupItem1.getLookupString();
        String s2 = lookupItem2.getLookupString();
        int diff1 = 0;
        for (int i = 0; i < Math.min(s1.length(), myOldReferenceName.length()); i++) {
          if (s1.charAt(i) != myOldReferenceName.charAt(i)) diff1++;
        }
        int diff2 = 0;
        for (int i = 0; i < Math.min(s2.length(), myOldReferenceName.length()); i++) {
          if (s2.charAt(i) != myOldReferenceName.charAt(i)) diff2++;
        }
        return diff1 - diff2;
      }
    }

    ReferenceNameExpression(LookupElement[] items, String oldReferenceName) {
      myItems = items;
      myOldReferenceName = oldReferenceName;
      Arrays.sort(myItems, new HammingComparator());
    }

    LookupElement[] myItems;
    private final String myOldReferenceName;

    @Override
    public Result calculateResult(ExpressionContext context) {
      if (myItems == null || myItems.length == 0) {
        return new TextResult(myOldReferenceName);
      }
      return new TextResult(myItems[0].getLookupString());
    }

    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      if (myItems == null || myItems.length == 1) return null;
      return myItems;
    }
  }
}
