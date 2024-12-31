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

package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PyNames;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.application.util.AsyncFileService;
import consulo.application.util.function.Processor;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.io.FileUtil;

import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CleanPycAction extends AnAction
{
	private static void collectPycFiles(File directory, final List<File> pycFiles)
	{
		FileUtil.processFilesRecursively(directory, new Processor<File>()
		{
			@Override
			public boolean process(File file)
			{
				if(file.getParentFile().getName().equals(PyNames.PYCACHE) ||
						FileUtil.extensionEquals(file.getName(), "pyc") ||
						FileUtil.extensionEquals(file.getName(), "pyo"))
				{
					pycFiles.add(file);
				}
				return true;
			}
		});
	}

	private static boolean isAllDirectories(@Nullable PsiElement[] elements)
	{
		if(elements == null || elements.length == 0)
		{
			return false;
		}
		for(PsiElement element : elements)
		{
			if(!(element instanceof PsiDirectory))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public void actionPerformed(AnActionEvent e)
	{
		final PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
		if(elements == null)
		{
			return;
		}
		List<File> pycFiles = new ArrayList<File>();
		ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable()
		{
			@Override
			public void run()
			{
				for(PsiElement element : elements)
				{
					PsiDirectory dir = (PsiDirectory) element;
					collectPycFiles(new File(dir.getVirtualFile().getPath()), pycFiles);
				}
				Application.get().getInstance(AsyncFileService.class).asyncDelete(pycFiles);
			}
		}, "Cleaning up .py files...", false, e.getData(Project.KEY));
		final StatusBar statusBar = WindowManager.getInstance().getIdeFrame(e.getData(Project.KEY)).getStatusBar();
		statusBar.setInfo("Deleted " + pycFiles.size() + " bytecode file" + (pycFiles.size() > 1 ? "s" : ""));
	}

	@Override
	public void update(AnActionEvent e)
	{
		if(e.getPresentation().isVisible())
		{
			final PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
			e.getPresentation().setEnabled(isAllDirectories(elements));
		}
	}
}
