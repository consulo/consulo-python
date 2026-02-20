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
package com.jetbrains.python.impl.refactoring.classes.extractSuperclass;

import com.google.common.base.Predicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.document.Document;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.event.RefactoringEventListener;
import consulo.language.psi.*;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.JBIterable;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/**
 * @author Dennis.Ushakov
 */
public final class PyExtractSuperclassHelper {
  private static final Logger LOG = Logger.getInstance(PyExtractSuperclassHelper.class);
  /**
   * Accepts only those members whose element is PyClass object (new classes)
   */
  private static final Predicate<PyMemberInfo<PyElement>> ALLOW_OBJECT = new PyUtil.ObjectPredicate(true);

  private PyExtractSuperclassHelper() {
  }

  static void extractSuperclass(PyClass clazz,
                                @Nonnull Collection<PyMemberInfo<PyElement>> selectedMemberInfos,
                                String superBaseName,
                                String targetFile) {
    Project project = clazz.getProject();

    //We will need to change it probably while param may be read-only
    //noinspection AssignmentToMethodParameter
    selectedMemberInfos = new ArrayList<>(selectedMemberInfos);

    RefactoringEventData beforeData = new RefactoringEventData();
    beforeData.addElements(JBIterable.from(selectedMemberInfos)
                                     .transform((Function<PyMemberInfo<PyElement>, PsiElement>)MemberInfoBase::getMember)
                                     .toList());

    project.getMessageBus()
           .syncPublisher(RefactoringEventListener.class)
           .refactoringStarted(getRefactoringId(), beforeData);

    // PY-12171
    PyMemberInfo<PyElement> objectMember = MembersManager.findMember(selectedMemberInfos, ALLOW_OBJECT);
    if (LanguageLevel.forElement(clazz).isPy3K() && !isObjectParentDeclaredExplicitly(clazz)) {
      // Remove object from list if Py3
      if (objectMember != null) {
        selectedMemberInfos.remove(objectMember);
      }
    }
    else {
      // Always add object if < Py3
      if (objectMember == null) {
        PyMemberInfo<PyElement> object = MembersManager.findMember(clazz, ALLOW_OBJECT);
        if (object != null) {
          selectedMemberInfos.add(object);
        }
      }
    }


    String text = "class " + superBaseName + ":\n  pass" + "\n";
    PyClass newClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class, text);

    newClass = placeNewClass(project, newClass, clazz, targetFile);
    MembersManager.moveAllMembers(selectedMemberInfos, clazz, newClass);
    if (!newClass.getContainingFile().equals(clazz.getContainingFile())) {
      PyClassRefactoringUtil.optimizeImports(clazz.getContainingFile()); // To remove unneeded imports only if user used different file
    }
    PyClassRefactoringUtil.addSuperclasses(project, clazz, null, newClass);

    RefactoringEventData afterData = new RefactoringEventData();
    afterData.addElement(newClass);
    project.getMessageBus().syncPublisher(RefactoringEventListener.class).refactoringDone(getRefactoringId(), afterData);
  }

  /**
   * If class explicitly extends object we shall move it even in Py3K
   */
  private static boolean isObjectParentDeclaredExplicitly(@Nonnull PyClass clazz) {
    return Arrays.stream(clazz.getSuperClassExpressions()).filter(o -> PyNames.OBJECT.equals(o.getName())).findFirst().isPresent();
  }

  private static PyClass placeNewClass(Project project, PyClass newClass, @Nonnull PyClass clazz, String targetFile) {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(VirtualFileUtil.pathToUrl(targetFile));
    // file is the same as the source
    if (Comparing.equal(file, clazz.getContainingFile().getVirtualFile())) {
      return (PyClass)clazz.getParent().addBefore(newClass, clazz);
    }

    PsiFile psiFile = null;
    try {
      if (file == null) {
        // file does not exist
        String filename;
        String path;
        if (targetFile.endsWith(PythonFileType.INSTANCE.getDefaultExtension())) {
          path = PathUtil.getParentPath(targetFile);
          filename = PathUtil.getFileName(targetFile);
        }
        else {
          path = targetFile;
          filename = PyNames.INIT_DOT_PY; // user requested putting the class into this package directly
        }
        psiFile = placeFile(project, path, filename);
      }
      else if (file.isDirectory()) { // existing directory
        psiFile = placeFile(project, file.getPath(), PyNames.INIT_DOT_PY);
      }
      else { // existing file
        psiFile = PsiManager.getInstance(project).findFile(file);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    LOG.assertTrue(psiFile != null);
    if (psiFile.getLastChild() != null) {
      // TODO: make the number of newlines depend on style setting
      psiFile.add(PyElementGenerator.getInstance(project).createFromText(LanguageLevel.PYTHON24, PsiWhiteSpace.class, "\n\n"));
    }
    newClass = (PyClass)psiFile.add(newClass);
    PyClassRefactoringUtil.insertImport(clazz, Collections.singleton((PsiNamedElement)newClass));
    return newClass;
  }

  /**
   * Places a file at the end of given path, creating intermediate dirs and inits.
   *
   * @param project
   * @param path
   * @param filename
   * @return the placed file
   * @throws IOException
   */
  public static PsiFile placeFile(Project project, String path, String filename) throws IOException {
    return placeFile(project, path, filename, null);
  }

  //TODO: Mover to the other class? That is not good to dependent PyUtils on this class
  public static PsiFile placeFile(Project project, String path, String filename, @Nullable String content) throws IOException {
    PsiDirectory psiDir = createDirectories(project, path);
    LOG.assertTrue(psiDir != null);
    PsiFile psiFile = psiDir.findFile(filename);
    if (psiFile == null) {
      psiFile = psiDir.createFile(filename);
      if (content != null) {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        Document document = manager.getDocument(psiFile);
        if (document != null) {
          document.setText(content);
          manager.commitDocument(document);
        }
      }
    }
    return psiFile;
  }

  /**
   * Create all intermediate dirs with inits from one of roots up to target dir.
   *
   * @param project
   * @param target  a full path to target dir
   * @return deepest child directory, or null if target is not in roots or process fails at some point.
   */
  @Nullable
  private static PsiDirectory createDirectories(Project project, String target) throws IOException {
    String the_rest = null;
    VirtualFile the_root = null;
    PsiDirectory ret = null;

    // NOTE: we don't canonicalize target; must be ok in reasonable cases, and is far easier in unit test mode
    target = FileUtil.toSystemIndependentName(target);
    for (VirtualFile file : ProjectRootManager.getInstance(project).getContentRoots()) {
      String root_path = file.getPath();
      if (target.startsWith(root_path)) {
        the_rest = target.substring(root_path.length());
        the_root = file;
        break;
      }
    }
    if (the_root == null) {
      throw new IOException("Can't find '" + target + "' among roots");
    }
    if (the_rest != null) {
      LocalFileSystem lfs = LocalFileSystem.getInstance();
      PsiManager psi_mgr = PsiManager.getInstance(project);
      String[] dirs = the_rest.split("/");
      int i = 0;
      if ("".equals(dirs[0])) {
        i = 1;
      }
      while (i < dirs.length) {
        VirtualFile subdir = the_root.findChild(dirs[i]);
        if (subdir != null) {
          if (!subdir.isDirectory()) {
            throw new IOException("Expected dir, but got non-dir: " + subdir.getPath());
          }
        }
        else {
          subdir = the_root.createChildDirectory(lfs, dirs[i]);
        }
        VirtualFile init_vfile = subdir.findChild(PyNames.INIT_DOT_PY);
        if (init_vfile == null) {
          init_vfile = subdir.createChildData(lfs, PyNames.INIT_DOT_PY);
        }
    /*
        // here we could add an __all__ clause to the __init__.py.
        // * there's no point to do so; we import the class directly;
        // * we can't do this consistently since __init__.py may already exist and be nontrivial.
        if (i == dirs.length - 1) {
          PsiFile init_file = psi_mgr.findFile(init_vfile);
          LOG.assertTrue(init_file != null);
          final PyElementGenerator gen = PyElementGenerator.getInstance(project);
          final PyStatement statement = gen.createFromText(LanguageLevel.getDefault(), PyStatement.class, PyNames.ALL + " = [\"" + lastName + "\"]");
          init_file.add(statement);
        }
        */
        the_root = subdir;
        i += 1;
      }
      ret = psi_mgr.findDirectory(the_root);
    }
    return ret;
  }

  public static String getRefactoringId() {
    return "refactoring.python.extract.superclass";
  }
}
