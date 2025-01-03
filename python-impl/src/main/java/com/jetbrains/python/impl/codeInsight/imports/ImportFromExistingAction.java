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
package com.jetbrains.python.impl.codeInsight.imports;

import consulo.language.editor.hint.QuestionAction;
import consulo.dataContext.DataManager;
import consulo.language.inject.InjectedLanguageManager;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.util.QualifiedName;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.JBList;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.icon.IconDescriptorUpdaters;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Turns an unqualified unresolved identifier into qualified and resolvable.
 *
 * @author dcheryasov
 */
public class ImportFromExistingAction implements QuestionAction
{
	PsiElement myTarget;
	List<ImportCandidateHolder> mySources; // list of <import, imported_item>
	String myName;
	boolean myUseQualifiedImport;
	private Runnable myOnDoneCallback;
	private final boolean myImportLocally;

	/**
	 * @param target       element to become qualified as imported.
	 * @param sources      clauses of import to be used.
	 * @param name         relevant name ot the target element (e.g. of identifier in an expression).
	 * @param useQualified if True, use qualified "import modulename" instead of "from modulename import ...".
	 */
	public ImportFromExistingAction(@Nonnull PsiElement target, @Nonnull List<ImportCandidateHolder> sources, @Nonnull String name, boolean useQualified, boolean importLocally)
	{
		myTarget = target;
		mySources = sources;
		myName = name;
		myUseQualifiedImport = useQualified;
		myImportLocally = importLocally;
	}

	public void onDone(Runnable callback)
	{
		assert myOnDoneCallback == null;
		myOnDoneCallback = callback;
	}


	/**
	 * Alters either target (by qualifying a name) or source (by explicitly importing the name).
	 *
	 * @return true if action succeeded
	 */
	public boolean execute()
	{
		// check if the tree is sane
		PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
		PyPsiUtils.assertValid(myTarget);
		if((myTarget instanceof PyQualifiedExpression) && ((((PyQualifiedExpression) myTarget).isQualified())))
		{
			return false; // we cannot be qualified
		}
		for(ImportCandidateHolder item : mySources)
		{
			PyPsiUtils.assertValid(item.getImportable());
			PyPsiUtils.assertValid(item.getFile());
			final PyImportElement element = item.getImportElement();
			if(element != null)
			{
				PyPsiUtils.assertValid(element);
			}
		}
		if(mySources.isEmpty())
		{
			return false;
		}
		// act
		if(mySources.size() == 1 || ApplicationManager.getApplication().isUnitTestMode())
		{
			doWriteAction(mySources.get(0));
		}
		else
		{
			selectSourceAndDo();
		}
		return true;
	}

	private void selectSourceAndDo()
	{
		// GUI part
		ImportCandidateHolder[] items = mySources.toArray(new ImportCandidateHolder[mySources.size()]); // silly JList can't handle modern collections
		final JList list = new JBList(items);
		list.setCellRenderer(new CellRenderer(myName));

		final Runnable runnable = () -> {
			final Object selected = list.getSelectedValue();
			if(selected instanceof ImportCandidateHolder)
			{
				final ImportCandidateHolder item = (ImportCandidateHolder) selected;
				PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
				doWriteAction(item);
			}
		};

		DataManager.getInstance().getDataContextFromFocus().doWhenDone(dataContext -> new PopupChooserBuilder(list).setTitle(myUseQualifiedImport ? PyBundle.message("ACT.qualify.with.module") : PyBundle.message("ACT.from.some.module.import")).setItemChoosenCallback(runnable).setFilteringEnabled(o -> ((ImportCandidateHolder) o).getPresentableText(myName)).createPopup().showInBestPositionFor(dataContext));
	}

	private void doIt(final ImportCandidateHolder item)
	{
		PyImportElement src = item.getImportElement();
		if(src != null)
		{
			addToExistingImport(src);
		}
		else
		{ // no existing import, add it then use it
			addImportStatement(item);
		}
	}

	private void addImportStatement(ImportCandidateHolder item)
	{
		final Project project = myTarget.getProject();
		final PyElementGenerator gen = PyElementGenerator.getInstance(project);
		AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(myTarget, item.getFile());
		PsiFile file = myTarget.getContainingFile();
		InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
		if(manager.isInjectedFragment(file))
		{
			file = manager.getTopLevelFile(myTarget);
		}
		// We are trying to import top-level module or package which thus cannot be qualified
		if(isRoot(item.getFile()))
		{
			if(myImportLocally)
			{
				AddImportHelper.addLocalImportStatement(myTarget, myName);
			}
			else
			{
				AddImportHelper.addImportStatement(file, myName, item.getAsName(), priority, null);
			}
		}
		else
		{
			final QualifiedName path = item.getPath();
			final String qualifiedName = path != null ? path.toString() : "";
			if(myUseQualifiedImport)
			{
				String nameToImport = qualifiedName;
				if(item.getImportable() instanceof PsiFileSystemItem)
				{
					nameToImport += "." + myName;
				}
				if(myImportLocally)
				{
					AddImportHelper.addLocalImportStatement(myTarget, nameToImport);
				}
				else
				{
					AddImportHelper.addImportStatement(file, nameToImport, item.getAsName(), priority, null);
				}
				myTarget.replace(gen.createExpressionFromText(LanguageLevel.forElement(myTarget), qualifiedName + "." + myName));
			}
			else
			{
				if(myImportLocally)
				{
					AddImportHelper.addLocalFromImportStatement(myTarget, qualifiedName, myName);
				}
				else
				{
					AddImportHelper.addFromImportStatement(file, qualifiedName, myName, item.getAsName(), priority, null);
				}
			}
		}
	}


	private void addToExistingImport(PyImportElement src)
	{
		final PyElementGenerator gen = PyElementGenerator.getInstance(myTarget.getProject());
		// did user choose 'import' or 'from import'?
		PsiElement parent = src.getParent();
		if(parent instanceof PyFromImportStatement)
		{
			// add another import element right after the one we got
			PsiElement newImportElement = gen.createImportElement(LanguageLevel.getDefault(), myName);
			parent.add(newImportElement);
		}
		else
		{ // just 'import'
			// all we need is to qualify our target
			myTarget.replace(gen.createExpressionFromText(LanguageLevel.forElement(myTarget), src.getVisibleName() + "." + myName));
		}
	}

	private void doWriteAction(final ImportCandidateHolder item)
	{
		PsiElement src = item.getImportable();
		new WriteCommandAction(src.getProject(), PyBundle.message("ACT.CMD.use.import"), myTarget.getContainingFile())
		{
			@Override
			protected void run(@Nonnull Result result) throws Throwable
			{
				doIt(item);
			}
		}.execute();
		if(myOnDoneCallback != null)
		{
			myOnDoneCallback.run();
		}
	}

	public static boolean isRoot(PsiFileSystemItem directory)
	{
		if(directory == null)
		{
			return true;
		}
		VirtualFile vFile = directory.getVirtualFile();
		if(vFile == null)
		{
			return true;
		}
		ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(directory.getProject());
		return Comparing.equal(fileIndex.getClassRootForFile(vFile), vFile) || Comparing.equal(fileIndex.getContentRootForFile(vFile), vFile) || Comparing.equal(fileIndex.getSourceRootForFile(vFile), vFile);
	}

	// Stolen from FQNameCellRenderer
	private static class CellRenderer extends SimpleColoredComponent implements ListCellRenderer
	{
		private final Font FONT;
		private final String myName;

		public CellRenderer(String name)
		{
			myName = name;
			EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
			FONT = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
			setOpaque(true);
		}

		// value is a QualifiedHolder
		public Component getListCellRendererComponent(JList list, Object value, // expected to be
				int index, boolean isSelected, boolean cellHasFocus)
		{

			clear();

			ImportCandidateHolder item = (ImportCandidateHolder) value;
			setIcon(IconDescriptorUpdaters.getIcon(item.getImportable(), 0));
			String item_name = item.getPresentableText(myName);
			append(item_name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

			setFont(FONT);
			if(isSelected)
			{
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else
			{
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			return this;
		}
	}
}
