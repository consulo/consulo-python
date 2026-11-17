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

import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMembersUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.vp.BadDataException;
import com.jetbrains.python.impl.refactoring.classes.membersManager.vp.MembersBasedPresenterNoPreviewImpl;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.fileEditor.FileEditorManager;
import consulo.language.psi.PsiManager;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * @author Ilya.Kazakevich
 */
public class PyExtractSuperclassPresenterImpl extends MembersBasedPresenterNoPreviewImpl<PyExtractSuperclassView, MemberInfoModel<PyElement, PyMemberInfo<PyElement>>> implements PyExtractSuperclassPresenter {
  private final NamesValidator myNamesValidator = NamesValidator.forLanguage(PythonLanguage.getInstance());

  public PyExtractSuperclassPresenterImpl(PyExtractSuperclassView view,
                                   PyClass classUnderRefactoring,
                                   PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage, new PyExtractSuperclassInfoModel(classUnderRefactoring));
  }

  @Override
  protected void validateView() throws BadDataException {
    super.validateView();
    Project project = myClassUnderRefactoring.getProject();
    if (!myNamesValidator.isIdentifier(myView.getSuperClassName(), project)) {
      throw new BadDataException(PyLocalize.refactoringExtractSuperName0MustBeIdent(myView.getSuperClassName()).get());
    }
    boolean rootFound = false;
    File moduleFile = new File(myView.getModuleFile());
    try {
      String targetDir = FileUtil.toSystemIndependentName(moduleFile.getCanonicalPath());
      for (VirtualFile file : ProjectRootManager.getInstance(project).getContentRoots()) {
        if (StringUtil.startsWithIgnoreCase(targetDir, file.getPath())) {
          rootFound = true;
          break;
        }
      }
    }
    catch (IOException ignore) {
    }
    if (!rootFound) {
      throw new BadDataException(PyLocalize.refactoringExtractSuperTargetPathOutsideRoots().get());
    }

    // TODO: Cover with test. It can't be done for now, because testFixture reports root path incorrectly
    // PY-12173
    myView.getModuleFile();
    final VirtualFile moduleVirtualFile = StandardFileSystems.local().findFileByPath(moduleFile.getAbsolutePath());
    if (moduleVirtualFile != null) {
      final PyFile pyFile = getPyFile(project, moduleVirtualFile);
      if (pyFile != null) {
        if (pyFile.findTopLevelClass(myView.getSuperClassName()) != null) {
          throw new BadDataException(PyLocalize.refactoringExtractSuperTargetClassAlreadyExists(myView.getSuperClassName()).get());
        }
      }
    }
  }

  private static @Nullable PyFile getPyFile(Project project, VirtualFile virtualFile) {
    // TODO consulo: BackedVirtualFile missing - JB picks the editor file which is a BackedVirtualFile
    VirtualFile targetFile = Arrays.stream(FileEditorManager.getInstance(project).getAllEditors(virtualFile))
      .map(editor -> editor.getFile())
      .filter(file -> false /* file instanceof BackedVirtualFile */)
      .findFirst()
      .orElse(virtualFile);
    return (PyFile)ContainerUtil.find(
      Objects.requireNonNull(PsiManager.getInstance(project).findFile(targetFile))
        .getViewProvider()
        .getAllFiles(), file -> file instanceof PyFile);
  }

  @Override
  public void launch() {
    String defaultFilePath = FileUtil.toSystemDependentName(myClassUnderRefactoring.getContainingFile().getVirtualFile().getPath());
    VirtualFile[] roots = ProjectRootManager.getInstance(myClassUnderRefactoring.getProject()).getContentRoots();
    Collection<PyMemberInfo<PyElement>> pyMemberInfos =
      PyMembersUtil.filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring));
    myView.configure(new PyExtractSuperclassInitializationInfo(myModel, pyMemberInfos, defaultFilePath, roots));
    myView.initAndShow();
  }

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("extract.superclass.command.name", myView.getSuperClassName(), myClassUnderRefactoring.getName());
  }

  @Override
  protected void refactorNoPreview() {
    var infos = myView.getSelectedMemberInfos();
    disableItemsCantBeAbstract(infos);
    PyExtractSuperclassHelper
      .extractSuperclass(myClassUnderRefactoring, infos, myView.getSuperClassName(), myView.getModuleFile());
  }

  private void disableItemsCantBeAbstract(Collection<PyMemberInfo<PyElement>> infos) {
    for (var info : infos) {
      if (info.isToAbstract() && !myModel.isAbstractEnabled(info)) {
        info.setToAbstract(false);
      }
    }
  }

  @Override
  protected Iterable<? extends PyClass> getDestClassesToCheckConflicts() {
    return Collections.emptyList(); // No conflict can take place in newly created classes
  }
}
