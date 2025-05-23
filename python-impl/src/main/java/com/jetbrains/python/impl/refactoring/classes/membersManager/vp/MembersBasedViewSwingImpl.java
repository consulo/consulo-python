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
package com.jetbrains.python.impl.refactoring.classes.membersManager.vp;

import com.google.common.base.Preconditions;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.MembersConflictDialog;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.ui.PyMemberSelectionPanel;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.MultiMap;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * {@link MembersBasedView} implementation on swing.
 * Consists of {@link #myTopPanel} and {@link #myCenterPanel}. Children must fill them in constructor.
 * Presenter is stored in {@link #myPresenter}.
 * Panel with members in {@link #myPyMemberSelectionPanel}
 *
 * @param <P> View presenter class
 * @param <C> View configuration class
 */
public abstract class MembersBasedViewSwingImpl<P extends MembersBasedPresenter, C extends MembersViewInitializationInfo> extends RefactoringDialog implements MembersBasedView<C>
{

	/**
	 * Panel to be displayed on the top
	 */
	@Nonnull
	protected final JPanel myTopPanel;
	/**
	 * Panel to be displayed at the center
	 */
	@Nonnull
	protected final JComponent myCenterPanel;

	/**
	 * Presenter
	 */
	@Nonnull
	protected final P myPresenter;

	/**
	 * Panel with members
	 */
	@Nonnull
	protected final PyMemberSelectionPanel myPyMemberSelectionPanel;

	private boolean myConfigured;


	/**
	 * @param project         project this view runs
	 * @param presenter       view's presenter
	 * @param title           window title
	 * @param supportAbstract supports "abstract" column?
	 */
	protected MembersBasedViewSwingImpl(@Nonnull final Project project, @Nonnull final P presenter, @Nonnull final String title, final boolean supportAbstract)
	{
		super(project, true);
		myTopPanel = new JPanel(new BorderLayout());
		myCenterPanel = new JPanel(new BorderLayout());
		myPresenter = presenter;
		myPyMemberSelectionPanel = new PyMemberSelectionPanel(title, supportAbstract);
		//TODO: Take this from presenter to prevent inconsistence: now it is possible to create view that supports abstract backed by presenter that does not. And vice versa.
	}


	@Override
	public boolean showConflictsDialog(@Nonnull final MultiMap<PyClass, PyMemberInfo<?>> duplicatesConflict, @Nonnull final Collection<PyMemberInfo<?>> dependenciesConflicts)
	{
		Preconditions.checkArgument(!(duplicatesConflict.isEmpty() && dependenciesConflicts.isEmpty()), "Can't show dialog for empty conflicts");
		final DialogWrapper conflictsDialog = new MembersConflictDialog(myProject, duplicatesConflict, dependenciesConflicts);
		return conflictsDialog.showAndGet();
	}

	@Override
	public void showError(@Nonnull final String message)
	{
		Messages.showErrorDialog(getContentPane(), message);
	}

	@Override
	protected boolean hasPreviewButton()
	{
		return myPresenter.showPreview();
	}

	@Override
	protected void doAction()
	{
		myPresenter.okClicked();
	}

	@Nonnull
	@Override
	protected JComponent createNorthPanel()
	{
		return myTopPanel;
	}

	@Override
	public void close()
	{
		close(OK_EXIT_CODE);
	}

	@Override
	protected JComponent createCenterPanel()
	{
		return myCenterPanel;
	}

	@Nonnull
	@Override
	public Collection<PyMemberInfo<PyElement>> getSelectedMemberInfos()
	{
		return myPyMemberSelectionPanel.getSelectedMemberInfos();
	}

	@Override
	public void invokeRefactoring(@Nonnull final BaseRefactoringProcessor processor)
	{
		super.invokeRefactoring(processor);
	}

	@Override
	public void configure(@Nonnull final C configInfo)
	{
		Preconditions.checkArgument(!myConfigured, "Already configured");
		myConfigured = true;
		myPyMemberSelectionPanel.init(configInfo.getMemberInfoModel(), configInfo.getMemberInfos());
	}

	@Override
	public void initAndShow()
	{
		Preconditions.checkArgument(myConfigured, "Not configured, run 'configure' first!");
		init();
		myPyMemberSelectionPanel.redraw();  // To display errors for checked member
		show();
	}
}