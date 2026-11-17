// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.editor;

import com.jetbrains.rest.RestFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorPolicy;
import consulo.fileEditor.FileEditorProvider;
import consulo.fileEditor.TextEditor;
import consulo.fileEditor.TextEditorWithPreviewFactory;
import consulo.fileEditor.text.TextEditorProvider;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.inject.Inject;

// TODO consulo: RestEditorProvider (JB base class from the ReST plugin) missing, its accept/getEditorTypeId are carried here
@ExtensionImpl
public class RestSplitEditorProvider implements FileEditorProvider, DumbAware {
  private final TextEditorWithPreviewFactory myTextEditorWithPreviewFactory;

  @Inject
  public RestSplitEditorProvider(TextEditorWithPreviewFactory textEditorWithPreviewFactory) {
    myTextEditorWithPreviewFactory = textEditorWithPreviewFactory;
  }

  // RestEditorProvider
  @Override
  public boolean accept(Project project, VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, RestFileType.INSTANCE);
  }

  @RequiredUIAccess
  @Override
  public FileEditor createEditor(Project project, VirtualFile file) {
    TextEditor editor = (TextEditor)TextEditorProvider.getInstance().createEditor(project, file);
    return myTextEditorWithPreviewFactory.create(editor, new RestPreviewFileEditor(file, project), "TextEditorWithPreview");
  }

  // RestEditorProvider
  @Override
  public String getEditorTypeId() {
    return "restructured-text-editor";
  }

  @Override
  public FileEditorPolicy getPolicy() {
    // TODO consulo: FileEditorPolicy.HIDE_OTHER_EDITORS missing
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
