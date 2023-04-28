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

package com.jetbrains.python.actions;

import com.jetbrains.python.PyNames;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.fileTemplate.FileTemplateUtil;
import consulo.ide.IdeBundle;
import consulo.ide.IdeView;
import consulo.ide.impl.idea.ide.actions.CreateDirectoryOrPackageHandler;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.language.editor.CommonDataKeys;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiFileSystemItem;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.Messages;

/**
 * @author yole
 */
public class CreatePackageAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreatePackageAction.class);

  @Override
  public void actionPerformed(AnActionEvent e) {
    final IdeView view = e.getData(IdeView.KEY);
    if (view == null) {
      return;
    }
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    CreateDirectoryOrPackageHandler validator = new CreateDirectoryOrPackageHandler(project, directory, consulo.ide.impl.actions.CreateDirectoryOrPackageType.Package, ".") {
      @Override
      protected void createDirectories(String subDirName) {
        super.createDirectories(subDirName);
        PsiFileSystemItem element = getCreatedElement();
        if (element instanceof PsiDirectory) {
          createInitPyInHierarchy((PsiDirectory)element, directory);
        }
      }
    };
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.a.new.package.name"),
                                      IdeBundle.message("title.new.package"),
                                      Messages.getQuestionIcon(), "", validator);
    final PsiFileSystemItem result = validator.getCreatedElement();
    if (result != null) {
      view.selectElement(result);
    }
  }

  public static void createInitPyInHierarchy(PsiDirectory created, PsiDirectory ancestor) {
    do {
      createInitPy(created);
      created = created.getParent();
    } while(created != null && created != ancestor);
  }

  private static void createInitPy(PsiDirectory directory) {
    final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(directory.getProject());
    final FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
    if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
      return;
    }
    if (template != null) {
      try {
        FileTemplateUtil.createFromTemplate(template, PyNames.INIT_DOT_PY, fileTemplateManager.getDefaultVariables(), directory);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    else {
      final PsiFile file = PsiFileFactory.getInstance(directory.getProject()).createFileFromText(PyNames.INIT_DOT_PY, "");
      directory.add(file);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = isEnabled(e) && e.getPresentation().isEnabled();
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final IdeView ideView = e.getData(IdeView.KEY);
    if (project == null || ideView == null) {
      return false;
    }
    final PsiDirectory[] directories = ideView.getDirectories();
    if (directories.length == 0) {
      return false;
    }
    return true;
  }
}
