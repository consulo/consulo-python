// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.sdk;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.ex.awt.ComponentWithBrowseButton.BrowseFolderActionListener;
import consulo.ui.ex.awt.TextComponentAccessor;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.io.File;

/**
 * Logic for updating 2 fields: name for new directory and it's base location
 *
 * @author catherine
 */
public class LocationNameFieldsBinding {
    private boolean myModifyingLocation = false;
    private boolean myModifyingProjectName = false;
    private boolean myExternalModify = false;
    private String myBaseDir;
    private final String mySuggestedProjectName;

    public LocationNameFieldsBinding(
        @Nullable Project project,
        TextFieldWithBrowseButton locationField,
        JTextField nameField,
        String baseDir,
        String title
    ) {
        myBaseDir = baseDir;
        File suggestedProjectDirectory = FileUtil.findSequentNonexistentFile(new File(baseDir), "untitled", "");
        locationField.setText(suggestedProjectDirectory.toString());
        nameField.setDocument(new NameFieldDocument(nameField, locationField));
        mySuggestedProjectName = suggestedProjectDirectory.getName();
        nameField.setText(mySuggestedProjectName);
        nameField.selectAll();

        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        BrowseFolderActionListener<JTextField> listener =
            new BrowseFolderActionListener<JTextField>(
                title,
                "",
                locationField,
                project,
                descriptor,
                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
            ) {
                @Override
                protected void onFileChosen(@Nonnull VirtualFile chosenFile) {
                    myBaseDir = chosenFile.getPath();
                    if (isProjectNameChanged(nameField.getText()) && !nameField.getText().equals(chosenFile.getName())) {
                        myExternalModify = true;
                        locationField.setText(new File(chosenFile.getPath(), nameField.getText()).toString());
                        myExternalModify = false;
                    }
                    else {
                        myExternalModify = true;
                        locationField.setText(chosenFile.getPath());
                        nameField.setText(chosenFile.getName());
                        myExternalModify = false;
                    }
                }
            };
        locationField.addActionListener(listener);
        locationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@Nonnull DocumentEvent e) {
                if (myExternalModify) {
                    return;
                }
                myModifyingLocation = true;
                String path = locationField.getText().trim();
                path = StringUtil.trimEnd(path, File.separator);
                int ind = path.lastIndexOf(File.separator);
                if (ind != -1) {
                    String projectName = path.substring(ind + 1);
                    if (!nameField.getText().trim().isEmpty()) {
                        myBaseDir = path.substring(0, ind);
                    }
                    if (!projectName.equals(nameField.getText())) {
                        if (!myModifyingProjectName) {
                            nameField.setText(projectName);
                        }
                    }
                }
                myModifyingLocation = false;
            }
        });
    }

    private boolean isProjectNameChanged(@Nonnull String currentName) {
        return !currentName.equals(mySuggestedProjectName);
    }

    private class NameFieldDocument extends PlainDocument {
        NameFieldDocument(JTextField projectNameTextField, TextFieldWithBrowseButton locationField) {
            addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@Nonnull DocumentEvent e) {
                    if (!myModifyingLocation && !myExternalModify) {
                        myModifyingProjectName = true;
                        File f = new File(myBaseDir);
                        locationField.setText(new File(f, projectNameTextField.getText()).getPath());
                    }
                }
            });
        }

        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            StringBuilder sb = null;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                boolean replace = c == '\\' || c == '/' || Platform.current().os().isWindows() && (c == '|' || c == ':');
                if (replace) {
                    if (sb == null) {
                        sb = new StringBuilder(str.length());
                        sb.append(str, 0, i);
                    }
                    sb.append('_');
                }
                else if (sb != null) {
                    sb.append(c);
                }
            }
            if (sb != null) {
                str = sb.toString();
            }
            super.insertString(offs, str, a);
        }
    }
}
