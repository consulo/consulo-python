// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.TextComponentAccessor;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseMoveDialog extends RefactoringDialog {
    protected final JPanel myCenterPanel;
    protected final JPanel myExtraPanel;
    protected final TextFieldWithBrowseButton myBrowseFieldWithButton;
    protected final JBLabel myDescription;
    protected final JTextField mySourcePathField;

    public PyBaseMoveDialog(Project project, String sourcePath, String destinationPath) {
        super(project, true);
        // Consulo: the JB GUI Designer layout (4x2 grid) is built with FormBuilder
        myDescription = new JBLabel(PyLocalize.refactoringMoveModuleMembersDialogDescriptionSelection().get());
        myBrowseFieldWithButton = new TextFieldWithBrowseButton();
        myExtraPanel = new JPanel();
        myExtraPanel.setLayout(new BorderLayout(0, 0));
        mySourcePathField = new JTextField();
        mySourcePathField.setEditable(false);
        mySourcePathField.setMinimumSize(new Dimension(150, -1));
        myCenterPanel = FormBuilder.createFormBuilder()
            .addComponent(myDescription)
            .addLabeledComponent(new JBLabel(PyLocalize.formMoveModuleMembersFrom().get()), mySourcePathField)
            .addLabeledComponent(new JBLabel(PyLocalize.formMoveModuleMembersTo().get()), myBrowseFieldWithButton)
            .addComponentFillVertically(myExtraPanel, 0)
            .getPanel();

        mySourcePathField.setText(sourcePath);
        myBrowseFieldWithButton.setText(destinationPath);
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withTitle(PyLocalize.refactoringMoveChooseDestinationFileTitle())
            .withRoots(ProjectRootManager.getInstance(project).getContentRoots())
            .withTreeRootVisible(true);
        myBrowseFieldWithButton.addBrowseFolderListener(LocalizeValue.empty(), LocalizeValue.empty(), project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    }

    @Override
    protected void init() {
        super.init();
        setUpDialog();
    }

    protected void setUpDialog() {
        preselectLastPathComponent(myBrowseFieldWithButton.getTextField());
    }

    protected static void preselectLastPathComponent(JTextField field) {
        String text = field.getText();
        int start = text.lastIndexOf(File.separatorChar);
        int lastDotIndex = text.lastIndexOf('.');
        int end = lastDotIndex < 0 ? text.length() : lastDotIndex;
        if (start + 1 < end) {
            field.select(start + 1, end);
        }
        // TODO consulo: DialogWrapperPeer.HAVE_INITIAL_SELECTION missing (consulo.ui.ex.awt.internal is not exported)
        // field.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);
    }

    @Override
    protected abstract String getHelpId();

    @Override
    protected abstract @Nullable String getDimensionServiceKey();

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return myCenterPanel;
    }

    @Override
    protected void doAction() {
        close(OK_EXIT_CODE);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myBrowseFieldWithButton.getTextField();
    }

    public String getTargetPath() {
        return myBrowseFieldWithButton.getText();
    }
}
