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

import com.jetbrains.python.impl.PyBundle;
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
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassPresenterImpl extends MembersBasedPresenterNoPreviewImpl<PyExtractSuperclassView, MemberInfoModel<PyElement, PyMemberInfo<PyElement>>> implements PyExtractSuperclassPresenter {
  private final NamesValidator myNamesValidator = NamesValidator.forLanguage(PythonLanguage.getInstance());

  PyExtractSuperclassPresenterImpl(@Nonnull final PyExtractSuperclassView view,
                                   @Nonnull final PyClass classUnderRefactoring,
                                   @Nonnull final PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage, new PyExtractSuperclassInfoModel(classUnderRefactoring));
  }

  @Override
  protected void validateView() throws BadDataException {
    super.validateView();
    final Project project = myClassUnderRefactoring.getProject();
    if (!myNamesValidator.isIdentifier(myView.getSuperClassName(), project)) {
      throw new BadDataException(PyBundle.message("refactoring.extract.super.name.0.must.be.ident", myView.getSuperClassName()));
    }
    boolean rootFound = false;
    final File moduleFile = new File(myView.getModuleFile());
    try {
      final String targetDir = FileUtil.toSystemIndependentName(moduleFile.getCanonicalPath());
      for (final VirtualFile file : ProjectRootManager.getInstance(project).getContentRoots()) {
        if (StringUtil.startsWithIgnoreCase(targetDir, file.getPath())) {
          rootFound = true;
          break;
        }
      }
    }
    catch (final IOException ignore) {
    }
    if (!rootFound) {
      throw new BadDataException(PyBundle.message("refactoring.extract.super.target.path.outside.roots"));
    }

    // TODO: Cover with test. It can't be done for now, because testFixture reports root path incorrectly
    // PY-12173
    myView.getModuleFile();
    final VirtualFile moduleVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(moduleFile);
    if (moduleVirtualFile != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(moduleVirtualFile);
      if (psiFile instanceof PyFile) {
        if (((PyFile)psiFile).findTopLevelClass(myView.getSuperClassName()) != null) {
          throw new BadDataException(PyBundle.message("refactoring.extract.super.target.class.already.exists", myView.getSuperClassName()));
        }
      }
    }
  }

  @Override
  public void launch() {
    final String defaultFilePath = FileUtil.toSystemDependentName(myClassUnderRefactoring.getContainingFile().getVirtualFile().getPath());
    final VirtualFile[] roots = ProjectRootManager.getInstance(myClassUnderRefactoring.getProject()).getContentRoots();
    final Collection<PyMemberInfo<PyElement>> pyMemberInfos =
      PyUtil.filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring));
    myView.configure(new PyExtractSuperclassInitializationInfo(myModel, pyMemberInfos, defaultFilePath, roots));
    myView.initAndShow();
  }

  @Nonnull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("extract.superclass.command.name", myView.getSuperClassName(), myClassUnderRefactoring.getName());
  }

  @Override
  protected void refactorNoPreview() {
    PyExtractSuperclassHelper.extractSuperclass(myClassUnderRefactoring,
                                                myView.getSelectedMemberInfos(),
                                                myView.getSuperClassName(),
                                                myView.getModuleFile());
  }

  @Nonnull
  @Override
  protected Iterable<? extends PyClass> getDestClassesToCheckConflicts() {
    return Collections.emptyList(); // No conflict can take place in newly created classes
  }
}
