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
package com.jetbrains.python.impl.refactoring.classes.ui;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nonnull;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;

import com.google.common.base.Preconditions;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.ScrollPaneFactory;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;

/**
 * Panel that handles table with list of class members with selection checkboxes.
 *
 * @author Dennis.Ushakov
 */
public class PyMemberSelectionPanel extends JPanel
{
	private static final List<PyMemberInfo<PyElement>> EMPTY_MEMBER_INFO = Collections.emptyList();
	private final PyMemberSelectionTable myTable;
	private boolean myInitialized;


	/**
	 * Creates empty panel to be filled later by {@link #init(MemberInfoModel, java.util.Collection)}
	 *
	 * @param title
	 */
	public PyMemberSelectionPanel(@Nonnull String title, boolean supportAbstract)
	{
		this(title, EMPTY_MEMBER_INFO, null, supportAbstract);
	}

	/**
	 * Creates panel and fills its table (see {@link #init(MemberInfoModel, java.util.Collection)} ) with members info
	 *
	 * @param title      Title for panel
	 * @param memberInfo list of members
	 * @param model      model
	 */
	public PyMemberSelectionPanel(String title, List<PyMemberInfo<PyElement>> memberInfo, MemberInfoModel<PyElement, PyMemberInfo<PyElement>> model, boolean supportAbstract)
	{
		Border titledBorder = IdeBorderFactory.createTitledBorder(title, false);
		Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
		Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
		setBorder(border);
		setLayout(new BorderLayout());

		myTable = new PyMemberSelectionTable(memberInfo, model, supportAbstract);
		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);


		add(scrollPane, BorderLayout.CENTER);
	}


	/**
	 * Inits panel.
	 *
	 * @param memberInfoModel model to display memebers in table
	 * @param members         members to display
	 */
	public void init(@Nonnull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel, @Nonnull Collection<PyMemberInfo<PyElement>> members)
	{
		Preconditions.checkState(!myInitialized, "Already myInitialized");
		myTable.setMemberInfos(members);
		myTable.setMemberInfoModel(memberInfoModel);
		myTable.addMemberInfoChangeListener(memberInfoModel);
		myInitialized = true;
	}

	/**
	 * @return list of members, selected by user
	 */
	@Nonnull
	public Collection<PyMemberInfo<PyElement>> getSelectedMemberInfos()
	{
		Preconditions.checkState(myInitialized, "Call #init first");
		return myTable.getSelectedMemberInfos();
	}

	/**
	 * Redraws table. Call it when some new data is available.
	 */
	public void redraw()
	{
		myTable.redraw();
	}
}
