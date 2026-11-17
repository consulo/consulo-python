// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.editor;

import com.jetbrains.python.rest.RestPythonPreviewProvider;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorState;
import consulo.project.Project;
import consulo.python.rest.localize.PythonRestLocalize;
import consulo.ui.Component;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import kava.beans.PropertyChangeListener;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;

public class RestPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  private static final long PARSING_CALL_TIMEOUT_MS = 50L;
  private static final String NO_PREVIEW = "<h2>No preview available.</h2><br/><br/>";

  private static final long RENDERING_DELAY_MS = 20L;

  private final RestPreviewPanel myPanel;
  private final VirtualFile myFile;
  private final Project myProject;
  private final @Nullable Document myDocument;
  private final Alarm myPooledAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private final Alarm mySwingAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private final Object REQUESTS_LOCK = new Object();
  private @Nullable Runnable myLastRequest = null;

  private String myLastRenderedHtml = "";

  private @Nullable Component myUIComponent;

  public RestPreviewFileEditor(VirtualFile file, Project project) {
    myFile = file;
    myProject = project;
    myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    // TODO consulo: RestJcefHtmlPanel missing (JBCefApp/JBCefBrowser missing), RestSwingHtmlPanel always used
    myPanel = new RestSwingHtmlPanel();

    if (myDocument != null) {
      myDocument.addDocumentListener(new DocumentListener() {

        @Override
        public void beforeDocumentChange(DocumentEvent e) {
          myPooledAlarm.cancelAllRequests();
        }

        @Override
        public void documentChanged(final DocumentEvent e) {
          myPooledAlarm.addRequest(() -> updateHtml(), PARSING_CALL_TIMEOUT_MS);
        }
      }, this);
    }
  }

  @Override
  public Component getUIComponent() {
    if (myUIComponent == null) {
      myUIComponent = TargetAWT.wrap(getComponent());
    }
    return myUIComponent;
  }

  @Override
  public JComponent getComponent() {
    return myPanel.getComponent();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myPanel.getComponent();
  }

  @Override
  public String getName() {
    return PythonRestLocalize.restructuredtextHtmlPreviewEditorName().get();
  }

  @Override
  public void setState(FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
    myPooledAlarm.cancelAllRequests();
    myPooledAlarm.addRequest(() -> updateHtml(), 0);
  }

  /**
   * Is always run from pooled thread
   */
  private void updateHtml() {
    if (!myFile.isValid() || myDocument == null || Disposer.isDisposed(this)) {
      return;
    }

    final Pair<String, String> htmlAndError = RestPythonPreviewProvider.toHtml(myDocument.getText(), myFile, myProject);
    if (htmlAndError == null) return;

    String html = htmlAndError.getFirst();
    if (html.isEmpty()) {
      html = NO_PREVIEW + htmlAndError.getSecond();
    }

    // EA-75860: The lines to the top may be processed slowly; Since we're in pooled thread, we can be disposed already.
    if (!myFile.isValid() || Disposer.isDisposed(this)) {
      return;
    }

    synchronized (REQUESTS_LOCK) {
      if (myLastRequest != null) {
        mySwingAlarm.cancelRequest(myLastRequest);
      }
      String finalHtml = html;
      myLastRequest = () -> {
        if (!finalHtml.equals(myLastRenderedHtml)) {
          myLastRenderedHtml = finalHtml;
          myPanel.setHtml(myLastRenderedHtml);
        }

        myPanel.render();
        synchronized (REQUESTS_LOCK) {
          myLastRequest = null;
        }
      };
      // TODO consulo: ModalityState.stateForComponent missing, default modality of the alarm is used
      mySwingAlarm.addRequest(myLastRequest, RENDERING_DELAY_MS);
    }
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {
  }

  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myPanel);
  }
}
