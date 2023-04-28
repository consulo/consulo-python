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

package com.jetbrains.python.packaging.setupPy;

import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.PyUtil;
import consulo.dataContext.DataContext;
import consulo.fileTemplate.AttributesDefaults;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.IdeView;
import consulo.ide.action.ui.CreateFromTemplateDialog;
import consulo.ide.impl.idea.ide.fileTemplates.actions.CreateFromTemplateAction;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Collection;
import java.util.Properties;

/**
 * @author yole
 */
public class CreateSetupPyAction extends CreateFromTemplateAction
{
	private static final String AUTHOR_PROPERTY = "python.packaging.author";
	private static final String EMAIL_PROPERTY = "python.packaging.author.email";

	public CreateSetupPyAction()
	{
		super(FileTemplateManager.getDefaultInstance().getInternalTemplate("Setup Script"));
		getTemplatePresentation().setText("Create setup.py");
	}

	@Override
	public FileTemplate getTemplate()
	{
		// to ensure changes are picked up, reload the template on every call (PY-6681)
		return FileTemplateManager.getDefaultInstance().getInternalTemplate("Setup Script");
	}

	@Override
	public void update(AnActionEvent e)
	{
		final Module module = e.getData(LangDataKeys.MODULE);
		e.getPresentation().setEnabled(module != null && PyPackageUtil.findSetupPy(module) == null);
	}

	@Override
	public AttributesDefaults getAttributesDefaults(DataContext dataContext)
	{
		Project project = dataContext.getData(CommonDataKeys.PROJECT);
		final AttributesDefaults defaults = new AttributesDefaults("setup.py").withFixedName(true);
		if(project != null)
		{
			defaults.add("Package_name", project.getName());
			final PropertiesComponent properties = PropertiesComponent.getInstance();
			defaults.add("Author", properties.getValue(AUTHOR_PROPERTY, SystemProperties.getUserName()));
			defaults.add("Author_Email", properties.getValue(EMAIL_PROPERTY, ""));
			defaults.addPredefined("PackageList", getPackageList(dataContext));
			defaults.addPredefined("PackageDirs", getPackageDirs(dataContext));
		}
		return defaults;
	}

	private static String getPackageList(DataContext dataContext)
	{
		final Module module = dataContext.getData(LangDataKeys.MODULE);
		if(module != null)
		{
			return "['" + StringUtil.join(PyPackageUtil.getPackageNames(module), "', '") + "']";
		}
		return "[]";
	}

	private static String getPackageDirs(DataContext dataContext)
	{
		final Module module = dataContext.getData(LangDataKeys.MODULE);
		if(module != null)
		{
			final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
			if(sourceRoots.length > 0)
			{
				for(VirtualFile sourceRoot : sourceRoots)
				{
					// TODO notify if we have multiple source roots and can't build mapping automatically
					final VirtualFile contentRoot = ProjectFileIndex.getInstance(module.getProject()).getContentRootForFile(sourceRoot);
					if(contentRoot != null && !Comparing.equal(contentRoot, sourceRoot))
					{
						final String relativePath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '/');
						return "\n    package_dir={'': '" + relativePath + "'},";
					}
				}
			}
		}
		return "";
	}

	@Override
	protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view)
	{
		final Module module = dataContext.getData(LangDataKeys.MODULE);
		if(module != null)
		{
			final Collection<VirtualFile> sourceRoots = PyUtil.getSourceRoots(module);
			if(sourceRoots.size() > 0)
			{
				return PsiManager.getInstance(module.getProject()).findDirectory(sourceRoots.iterator().next());
			}
		}
		return super.getTargetDirectory(dataContext, view);
	}

	@Override
	protected void elementCreated(CreateFromTemplateDialog dialog, PsiElement createdElement)
	{
		final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
		final Properties properties = dialog.getEnteredProperties();
		final String author = properties.getProperty("Author");
		if(author != null)
		{
			propertiesComponent.setValue(AUTHOR_PROPERTY, author);
		}
		final String authorEmail = properties.getProperty("Author_Email");
		if(authorEmail != null)
		{
			propertiesComponent.setValue(EMAIL_PROPERTY, authorEmail);
		}
	}
}
