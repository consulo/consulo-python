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
package com.jetbrains.python.impl.refactoring.classes.pullUp;

import com.jetbrains.python.impl.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl;
import com.jetbrains.python.impl.refactoring.classes.ui.PyClassCellRenderer;
import com.jetbrains.python.psi.PyClass;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.JBUI;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Ilya.Kazakevich
 * Pull up view implementation
 */
class PyPullUpViewSwingImpl extends MembersBasedViewSwingImpl<PyPullUpPresenter, PyPullUpViewInitializationInfo> implements PyPullUpView, ItemListener {
    @Nonnull
    private final ComboBox myParentsCombo;
    @Nonnull
    private final DefaultComboBoxModel<PyClass> myParentsComboBoxModel;
    @Nonnull
    private final PyPullUpNothingToRefactorMessage myNothingToRefactorMessage;

    /**
     * @param project                  project where refactoring takes place
     * @param presenter                presenter for this view
     * @param clazz                    class to refactor
     * @param nothingToRefactorMessage class that displays message "nothing to refactor" when presenter calls {@link #showNothingToRefactor()}
     */
    PyPullUpViewSwingImpl(
        @Nonnull Project project,
        @Nonnull PyPullUpPresenter presenter,
        @Nonnull PyClass clazz,
        @Nonnull PyPullUpNothingToRefactorMessage nothingToRefactorMessage
    ) {
        super(project, presenter, RefactoringLocalize.membersToBePulledUp().get(), true);
        setTitle(PyPullUpHandler.REFACTORING_NAME);
        myNothingToRefactorMessage = nothingToRefactorMessage;

        myParentsComboBoxModel = new DefaultComboBoxModel<>();

        myParentsCombo = new ComboBox(myParentsComboBoxModel);
        myParentsCombo.setRenderer(new PyClassCellRenderer());

        JLabel mainLabel = new JLabel();
        mainLabel.setText(RefactoringLocalize.pullUpMembersTo(PyClassCellRenderer.getClassText(clazz)).get());
        mainLabel.setLabelFor(myParentsCombo);

        myTopPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbConstraints = new GridBagConstraints();

        gbConstraints.insets = JBUI.insets(4, 8);
        gbConstraints.weighty = 1;
        gbConstraints.weightx = 1;
        gbConstraints.gridy = 0;
        gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
        gbConstraints.fill = GridBagConstraints.BOTH;
        gbConstraints.anchor = GridBagConstraints.WEST;
        myTopPanel.add(mainLabel, gbConstraints);
        myTopPanel.add(mainLabel, gbConstraints);
        gbConstraints.gridy++;
        myTopPanel.add(myParentsCombo, gbConstraints);

        gbConstraints.gridy++;
        myCenterPanel.add(myPyMemberSelectionPanel, BorderLayout.CENTER);
    }

    @Nonnull
    @Override
    protected String getHelpId() {
        return "python.reference.pullMembersUp";
    }

    @Nonnull
    @Override
    public PyClass getSelectedParent() {
        return (PyClass) myParentsComboBoxModel.getSelectedItem();
    }

    @Override
    public void showNothingToRefactor() {
        myNothingToRefactorMessage.showNothingToRefactor();
    }

    @Override
    public void configure(@Nonnull PyPullUpViewInitializationInfo configInfo) {
        super.configure(configInfo);
        for (PyClass parent : configInfo.getParents()) {
            myParentsComboBoxModel.addElement(parent);
        }
        myPresenter.parentChanged();
        myParentsCombo.addItemListener(this);
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            myPyMemberSelectionPanel.redraw();
            myPresenter.parentChanged();
        }
    }
}
