// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.RefactoringSettings;
import consulo.language.editor.refactoring.move.MoveFileHandler;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.PathUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Couple;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtensionImpl
public final class PyMoveFileHandler extends MoveFileHandler {
  private static final Key<String> ORIGINAL_FILE_LOCATION = Key.create("PY_ORIGINAL_FILE_LOCATION");

  @Override
  public boolean canProcessElement(PsiFile element) {
    return element.getFileType() == PythonFileType.INSTANCE;
  }

  @Override
  public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    if (file != null) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        PyUtil.deletePycFiles(virtualFile.getPath());
      }
      final Collection<VirtualFile> roots = PyUtil.getSourceRoots(file);
      PsiDirectory root = moveDestination;
      while (root != null && !roots.contains(root.getVirtualFile())) {
        root = root.getParentDirectory();
      }
      final boolean searchForReferences = RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE;
      if (moveDestination != root && root != null && searchForReferences && !probablyNamespacePackage(file, moveDestination, root)) {
        PyMoveRefactoringUtil.createInitPyInHierarchy(moveDestination, root);
      }
    }
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
    if (file instanceof PyFile) {
      updateRelativeImportsInModule((PyFile)file);
    }
  }

  private static void updateRelativeImportsInModule(PyFile module) {
    final String originalLocation = getOriginalLocation(module);
    if (originalLocation == null) {
      return;
    }
    //module.putUserData(ORIGINAL_FILE_LOCATION, null);
    for (PyFromImportStatement statement : module.getFromImports()) {
      if (!canBeRelative(statement)) {
        continue;
      }
      final int relativeLevel = Math.max(statement.getRelativeLevel(), 1);
      final PsiFileSystemItem sourceElement = resolveRelativeImportFromModuleLocation(module,
                                                                                      originalLocation,
                                                                                      statement.getImportSource(),
                                                                                      relativeLevel);
      if (sourceElement == null) {
        continue;
      }
      final QualifiedName newName = QualifiedNameFinder.findShortestImportableQName(sourceElement);
      replaceRelativeImportSourceWithQualifiedExpression(statement, newName);
    }

    for (PyImportElement importElement : module.getImportTargets()) {
      final PyReferenceExpression referenceExpr = importElement.getImportReferenceExpression();
      if (!canBeRelative(importElement) || referenceExpr == null) {
        continue;
      }
      final PsiFileSystemItem resolved = resolveRelativeImportFromModuleLocation(module, originalLocation, referenceExpr, 1);
      if (resolved == null) {
        continue;
      }
      final QualifiedName newName = QualifiedNameFinder.findShortestImportableQName(resolved);
      replaceWithQualifiedExpression(referenceExpr, newName);
      final QualifiedName oldQualifiedName = referenceExpr.asQualifiedName();
      if (!Comparing.equal(oldQualifiedName, newName)) {
        final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(importElement);
        if (scopeOwner == null) {
          continue;
        }
        scopeOwner.accept(new PyRecursiveElementVisitor() {
          @Override
          public void visitPyReferenceExpression(PyReferenceExpression node) {
            if (Comparing.equal(node.asQualifiedName(), oldQualifiedName)) {
              replaceWithQualifiedExpression(node, newName);
            }
            else {
              super.visitPyReferenceExpression(node);
            }
          }
        });
      }
    }
  }

  private static boolean canBeRelative(PyFromImportStatement statement) {
    return LanguageLevel.forElement(statement).isPython2() || statement.getRelativeLevel() > 0;
  }


  private static boolean canBeRelative(PyImportElement statement) {
    return LanguageLevel.forElement(statement).isPython2();
  }

  /**
   * @param referenceExpr is null if we resolve import of type "from .. import bar", and "foo" for import of type "from foo import bar"
   */
  private static @Nullable PsiFileSystemItem resolveRelativeImportFromModuleLocation(PyFile context,
                                                                           String moduleLocation,
                                                                           @Nullable PyReferenceExpression referenceExpr,
                                                                           int relativeLevel) {
    String relativeImportBasePath = VirtualFileManager.extractPath(moduleLocation);
    for (int level = 0; level < relativeLevel; level++) {
      relativeImportBasePath = PathUtil.getParentPath(relativeImportBasePath);
    }
    if (!relativeImportBasePath.isEmpty()) {
      final String protocol = VirtualFileManager.extractProtocol(moduleLocation);
      assert protocol != null : "Original location: " + moduleLocation;
      final String relativeImportBaseUrl = VirtualFileManager.constructUrl(protocol, relativeImportBasePath);
      VirtualFile relativeImportBaseDir = VirtualFileManager.getInstance().findFileByUrl(relativeImportBaseUrl);
      VirtualFile sourceFile = relativeImportBaseDir;
      if (relativeImportBaseDir != null && relativeImportBaseDir.isDirectory() && referenceExpr != null) {
        final QualifiedName qualifiedName = referenceExpr.asQualifiedName();
        if (qualifiedName == null) {
          return null;
        }
        final String relativePath = qualifiedName.join("/");
        sourceFile = relativeImportBaseDir.findFileByRelativePath(relativePath);
        if (sourceFile == null) {
          sourceFile = relativeImportBaseDir.findFileByRelativePath(relativePath + PyNames.DOT_PY);
        }
      }
      if (sourceFile != null) {
        final PsiFileSystemItem sourceElement;
        if (sourceFile.isDirectory()) {
          sourceElement = context.getManager().findDirectory(sourceFile);
        }
        else {
          sourceElement = context.getManager().findFile(sourceFile);
        }
        return sourceElement;
      }
    }
    return null;
  }

  private static boolean probablyNamespacePackage(PsiFile anchor, PsiDirectory destination, PsiDirectory root) {
    if (LanguageLevel.forElement(anchor).isPython2()) {
      return false;
    }
    while (destination != null && destination != root) {
      if (destination.findFile(PyNames.INIT_DOT_PY) != null) {
        return false;
      }
      destination = destination.getParent();
    }
    return true;
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile file, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    file.putUserData(ORIGINAL_FILE_LOCATION, file.getVirtualFile().getUrl());
    final List<UsageInfo> usages = PyPsiIndexUtil.findUsages(file, false);
    return ContainerUtil.map(usages, usage -> {
      final PsiElement element = usage.getElement();
      if (element != null) {
        return new PyUsageInfo(element, file);
      }
      return usage;
    });
  }

  static final class PyUsageInfo extends UsageInfo {
    private final PsiFile myFile;

    PyUsageInfo(PsiElement element, PsiFile file) {
      super(element);
      myFile = file;
    }
  }

  @Override
  public void retargetUsages(List<UsageInfo> usages, Map<PsiElement, PsiElement> oldToNewMap) {
    final Set<PsiFile> updatedFiles = new HashSet<>();
    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement != null && usage instanceof PyUsageInfo) {
        final PsiNamedElement movedElement = ((PyUsageInfo)usage).myFile;
        final PsiFile usageFile = usageElement.getContainingFile();

        final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(usageElement, PyImportStatementBase.class);
        // TODO: Retarget qualified expressions in docstrings
        if (importStmt != null) {

          if (getOriginalLocation(usageFile) != null) {
            // Leave relative imports as they are after #updateRelativeImportsInModule
            final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(usageFile.getProject(), usageFile);
            final PyResolveContext resolveContext = PyResolveContext.implicitContext(typeEvalContext);
            if (ContainerUtil.getFirstItem(PyUtil.multiResolveTopPriority(usageElement, resolveContext)) == movedElement) {
              continue;
            }
          }

          updatedFiles.add(usageFile);
          final boolean usageInsideImportElement = PsiTreeUtil.getParentOfType(usageElement, PyImportElement.class) != null;
          if (usageInsideImportElement) {
            // Handles imported element in "from import" statement (from some.package import module)
            // or simple unqualified import of the module (import module).
            if (PyClassRefactoringUtil.updateUnqualifiedImportOfElement(importStmt, movedElement)) {
              continue;
            }
          }
          final QualifiedName newElementName = QualifiedNameFinder.findCanonicalImportPath(movedElement, usageElement);
          if (importStmt instanceof PyFromImportStatement) {
            if (!usageInsideImportElement) {
              replaceRelativeImportSourceWithQualifiedExpression((PyFromImportStatement)importStmt, newElementName);
            }
          }
          else {
            replaceWithQualifiedExpression(usageElement, newElementName);
          }
        }
        else if (usageElement instanceof PyReferenceExpression) {
          updatedFiles.add(usageFile);
          if (((PyReferenceExpression)usageElement).isQualified()) {
            final QualifiedName newQualifiedName = QualifiedNameFinder.findCanonicalImportPath(movedElement, usageElement);
            replaceWithQualifiedExpression(usageElement, newQualifiedName);
          }
          else {
            final QualifiedName newName = QualifiedName.fromComponents(PyPsiRefactoringUtil.getOriginalName(movedElement));
            replaceWithQualifiedExpression(usageElement, newName);
          }
        }
      }
    }
    if (!updatedFiles.isEmpty()) {
      final PyImportOptimizer optimizer = PyImportOptimizer.onlyRemoveUnused();
      for (PsiFile file : updatedFiles) {
        final boolean injectedFragment = InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
        if (!injectedFragment) {
          optimizer.processFile(file).run();
        }
      }
    }
  }

  private static @Nullable String getOriginalLocation(@Nullable PsiFile file) {
    return file == null ? null : file.getUserData(ORIGINAL_FILE_LOCATION);
  }

  private static PsiElement replaceWithQualifiedExpression(PsiElement oldElement, @Nullable QualifiedName newElementName) {
    if (newElementName != null && PyPsiRefactoringUtil.isValidQualifiedName(newElementName)) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(oldElement.getProject());
      final PsiElement newElement = generator.createExpressionFromText(LanguageLevel.forElement(oldElement), newElementName.toString());
      return oldElement.replace(newElement);
    }
    return oldElement;
  }

  /**
   * Replace import source with leading dots (if any) with reference expression created from given qualified name.
   * Basically it does the same thing as {@link #replaceWithQualifiedExpression}, but also removes leading dots.
   *
   * @param importStatement import statement to update
   * @param qualifiedName   qualified name of new import source
   * @return updated import statement
   * @see #replaceWithQualifiedExpression(PsiElement, QualifiedName)
   */
  private static PsiElement replaceRelativeImportSourceWithQualifiedExpression(PyFromImportStatement importStatement,
                                                                               @Nullable QualifiedName qualifiedName) {
    final Couple<PsiElement> range = getRelativeImportSourceRange(importStatement);
    if (range != null && qualifiedName != null) {
      if (range.getFirst() != range.getSecond()) {
        importStatement.deleteChildRange(range.getFirst().getNextSibling(), range.getSecond());
      }
      replaceWithQualifiedExpression(range.getFirst(), qualifiedName);
    }
    return importStatement;
  }

  private static @Nullable Couple<PsiElement> getRelativeImportSourceRange(PyFromImportStatement statement) {
    final PsiElement fromKeyword = statement.getFirstChild();
    assert fromKeyword.getNode().getElementType() == PyTokenTypes.FROM_KEYWORD;
    final PsiElement elementAfterFrom = PsiTreeUtil.skipWhitespacesForward(fromKeyword);
    if (elementAfterFrom == null) {
      return null;
    }
    else if (elementAfterFrom instanceof PyReferenceExpression) {
      return Couple.of(elementAfterFrom, elementAfterFrom);
    }
    else if (elementAfterFrom.getNode().getElementType() == PyTokenTypes.DOT) {
      PsiElement lastDot;
      PsiElement next = elementAfterFrom;
      do {
        lastDot = next;
        next = PsiTreeUtil.skipWhitespacesForward(next);
      }
      while (next != null && next.getNode().getElementType() == PyTokenTypes.DOT);
      if (next instanceof PyReferenceExpression) {
        return Couple.of(elementAfterFrom, next);
      }
      else {
        return Couple.of(elementAfterFrom, lastDot);
      }
    }
    return null;
  }
}
