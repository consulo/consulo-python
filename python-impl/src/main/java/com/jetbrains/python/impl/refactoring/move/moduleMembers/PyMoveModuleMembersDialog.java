// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.moduleMembers;

import com.jetbrains.python.impl.refactoring.move.PyBaseMoveDialog;
import com.jetbrains.python.impl.refactoring.move.PyMoveRefactoringUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.application.ApplicationPropertiesComponent;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.classMember.MemberInfoChange;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.language.editor.refactoring.ui.AbstractMemberSelectionTable;
import consulo.language.psi.PsiNamedElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectPropertiesComponent;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.awt.HideableDecorator;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyMoveModuleMembersDialog extends PyBaseMoveDialog {
    private static final String BULK_MOVE_TABLE_VISIBLE = "python.move.module.members.dialog.show.table";

    private final TopLevelSymbolsSelectionTable myMemberSelectionTable;
    private final PyModuleMemberInfoModel myModuleMemberModel;
    private final boolean mySeveralElementsSelected;

    /**
     * @param project     dialog project
     * @param elements    elements to move
     * @param destination destination where elements have to be moved
     */
    public PyMoveModuleMembersDialog(Project project, List<PsiNamedElement> elements, String source, String destination) {
        super(project, source, destination);

        assert !elements.isEmpty();
        PsiNamedElement firstElement = elements.get(0);
        setTitle(PyLocalize.refactoringMoveModuleMembersDialogTitle());

        PyFile pyFile = (PyFile) firstElement.getContainingFile();
        myModuleMemberModel = new PyModuleMemberInfoModel(pyFile);

        List<PyModuleMemberInfo> symbolsInfos = collectModuleMemberInfos(myModuleMemberModel.myPyFile);
        for (PyModuleMemberInfo info : symbolsInfos) {
            //noinspection SuspiciousMethodCalls
            info.setChecked(elements.contains(info.getMember()));
        }
        myModuleMemberModel.memberInfoChanged(new MemberInfoChange<>(symbolsInfos));
        myMemberSelectionTable = new TopLevelSymbolsSelectionTable(symbolsInfos, myModuleMemberModel);
        myMemberSelectionTable.addMemberInfoChangeListener(myModuleMemberModel);
        myMemberSelectionTable.getModel().addTableModelListener(e -> validateButtons());
        mySeveralElementsSelected = elements.size() > 1;
        boolean tableIsVisible =
            mySeveralElementsSelected || ApplicationPropertiesComponent.getInstance().getBoolean(BULK_MOVE_TABLE_VISIBLE);
        LocalizeValue description;
        if (!tableIsVisible && elements.size() == 1) {
            String name = PyMoveRefactoringUtil.getPresentableName(firstElement);
            if (firstElement instanceof PyFunction) {
                description = PyLocalize.refactoringMoveModuleMembersDialogDescriptionFunction(name);
            }
            else if (firstElement instanceof PyClass) {
                description = PyLocalize.refactoringMoveModuleMembersDialogDescriptionClass(name);
            }
            else {
                description = PyLocalize.refactoringMoveModuleMembersDialogDescriptionVariable(name);
            }
        }
        else {
            description = PyLocalize.refactoringMoveModuleMembersDialogDescriptionSelection();
        }
        myDescription.setText(description.get());
        HideableDecorator decorator =
            new HideableDecorator(myExtraPanel, PyLocalize.refactoringMoveModuleMembersDialogTableTitle().get(), true) {
                @Override
                protected void on() {
                    super.on();
                    myDescription.setText(PyLocalize.refactoringMoveModuleMembersDialogDescriptionSelection().get());
                    ApplicationPropertiesComponent.getInstance().setValue(BULK_MOVE_TABLE_VISIBLE, true);
                }

                @Override
                protected void off() {
                    super.off();
                    ApplicationPropertiesComponent.getInstance().setValue(BULK_MOVE_TABLE_VISIBLE, false);
                }
            };
        decorator.setOn(tableIsVisible);
        decorator.setContentComponent(new JBScrollPane(myMemberSelectionTable) {
            @Override
            public Dimension getMinimumSize() {
                // Prevent growth of the dialog after several expand/collapse actions
                return new Dimension((int) super.getMinimumSize().getWidth(), 0);
            }
        });

        init();
        initValidation();
    }

    @Override
    protected void setUpDialog() {
        super.setUpDialog();
        enlargeDialogHeightIfNecessary();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (!areButtonsValid()) {
            return new ValidationInfo(PyLocalize.refactoringMoveModuleMembersErrorSelectionEmpty());
        }
        return super.doValidate();
    }

    private void enlargeDialogHeightIfNecessary() {
        if (mySeveralElementsSelected && !ProjectPropertiesComponent.getInstance(getProject()).getBoolean(BULK_MOVE_TABLE_VISIBLE)) {
            // TODO consulo: DialogWrapperPeer missing (consulo.ui.ex.awt.internal is not exported)
            // final DialogWrapperPeer peer = getPeer();
            // final Dimension realSize = peer.getSize();
            // final double preferredHeight = peer.getPreferredSize().getHeight();
            // if (realSize.getHeight() < preferredHeight) {
            //   peer.setSize((int)realSize.getWidth(), (int)preferredHeight);
            // }
        }
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "#com.jetbrains.python.refactoring.move.PyMoveModuleMembersDialog";
    }

    @Override
    protected String getHelpId() {
        return "python.reference.moveModuleMembers";
    }

    @Override
    protected boolean areButtonsValid() {
        return !myMemberSelectionTable.getSelectedMemberInfos().isEmpty();
    }

    /**
     * @return selected elements in the same order as they are declared in the original file
     */
    public List<PyElement> getSelectedTopLevelSymbols() {
        Collection<PyModuleMemberInfo> selectedMembers = myMemberSelectionTable.getSelectedMemberInfos();
        List<PyElement> selectedElements = ContainerUtil.map(selectedMembers, MemberInfoBase::getMember);
        return ContainerUtil.sorted(selectedElements, (e1, e2) -> PyPsiUtils.isBefore(e1, e2) ? -1 : 1);
    }

    private static List<PyModuleMemberInfo> collectModuleMemberInfos(PyFile pyFile) {
        List<PyElement> moduleMembers = PyMoveModuleMembersHelper.getTopLevelModuleMembers(pyFile);
        return ContainerUtil.mapNotNull(moduleMembers, PyModuleMemberInfo::new);
    }

    static class TopLevelSymbolsSelectionTable extends AbstractMemberSelectionTable<PyElement, PyModuleMemberInfo> {
        TopLevelSymbolsSelectionTable(
            Collection<PyModuleMemberInfo> memberInfos,
            @Nullable MemberInfoModel<PyElement, PyModuleMemberInfo> memberInfoModel
        ) {
            super(memberInfos, memberInfoModel, null);
        }

        @Override
        protected @Nullable Object getAbstractColumnValue(PyModuleMemberInfo memberInfo) {
            return null;
        }

        @Override
        protected boolean isAbstractColumnEditable(int rowIndex) {
            return false;
        }

        // Consulo: AbstractMemberSelectionTable has no setVisibilityIcon()

        @Override
        protected Image getOverrideIcon(PyModuleMemberInfo memberInfo) {
            // JB returns null (no icon)
            return EMPTY_OVERRIDE_ICON;
        }
    }
}
