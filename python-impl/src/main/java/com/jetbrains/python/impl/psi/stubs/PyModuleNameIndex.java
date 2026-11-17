// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.DefaultFileTypeSpecificInputFilter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.PyFile;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtensionImpl
public final class PyModuleNameIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> NAME = ID.create("Py.module.name");

  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public Map<String, Void> map(FileContent inputData) {
        final VirtualFile file = inputData.getFile();
        final String name = file.getName();
        if (PyNames.INIT_DOT_PY.equals(name)) {
          final VirtualFile parent = file.getParent();
          if (parent != null && parent.isDirectory()) {
            return Collections.singletonMap(parent.getName(), null);
          }
        }
        else {
          return Collections.singletonMap(FileUtil.getNameWithoutExtension(name), null);
        }
        return Collections.emptyMap();
      }
    };
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    // TODO consulo: FileTypeInputFilterPredicate missing
    return new DefaultFileTypeSpecificInputFilter(PythonFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  public static Collection<String> getAllKeys(Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }

  public static List<PyFile> find(String shortName, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? PySearchUtilBase.excludeSdkTestsScope(project)
                                                           : GlobalSearchScope.projectScope(project);
    return findByShortName(shortName, project, scope);
  }

  /**
   * Returns all modules with the given short name (the last component of a fully qualified name).
   * <p>
   * File extensions should not be included. For __init__.py modules, the name of the corresponding directory is matched.
   *
   * @param shortName short name of a module or name of the containing package for __init__.py modules
   * @param project   project where the search is performed
   * @param scope     search scope, limiting applicable virtual files
   */
  public static List<PyFile> findByShortName(String shortName,
                                                      Project project,
                                                      GlobalSearchScope scope) {
    final List<PyFile> results = new ArrayList<>();
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, shortName, scope);
    for (VirtualFile virtualFile : files) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile instanceof PyFile) {
        results.add((PyFile)psiFile);
      }
    }
    return results;
  }

  /**
   * Returns all modules with the given fully qualified name.
   * <p>
   * For __init__.py modules, the qualified name of the corresponding package is used.
   * <p>
   * All possible qualified names of a module are considered. For instance, in case of a source root "src" inside a project's
   * content root, src/foo.py or src/foo/__init__.py will be returned both for qualified names "foo" and "src.foo".
   *
   * @param qName   short name of a module or name of the containing package for __init__.py modules
   * @param project project where the search is performed
   * @param scope   search scope, limiting applicable virtual files
   * @see QualifiedNameFinder#findImportableQNames(PsiElement, VirtualFile)
   */
  public static List<PyFile> findByQualifiedName(QualifiedName qName,
                                                          Project project,
                                                          GlobalSearchScope scope) {
    String shortName = qName.getLastComponent();
    if (shortName == null) return Collections.emptyList();
    return ContainerUtil.filter(findByShortName(shortName, project, scope), file -> {
      List<QualifiedName> possibleQNames = QualifiedNameFinder.findImportableQNames(file, file.getVirtualFile());
      return possibleQNames.contains(qName);
    });
  }
}
