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
package com.jetbrains.python.impl.console;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.impl.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.impl.console.parsing.PythonConsoleData;
import com.jetbrains.python.impl.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.impl.debugger.PyStackFrame;
import com.jetbrains.python.impl.highlighting.PyHighlighter;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorHighlighter;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.debug.frame.XStandaloneVariablesView;
import consulo.execution.debug.frame.XStandaloneVariablesViewFactory;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.console.ConsoleViewUtil;
import consulo.execution.ui.console.ObservableConsoleView;
import consulo.execution.ui.console.OpenFileHyperlinkInfo;
import consulo.language.editor.highlight.HighlighterFactory;
import consulo.language.editor.highlight.LexerEditorHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.inject.EditorWindow;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.process.ProcessOutputTypes;
import consulo.project.Project;
import consulo.ui.ex.awt.JBSplitter;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.concurrent.ActionCallback;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.TimeoutUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class PythonConsoleView extends consulo.ide.impl.idea.execution.console.LanguageConsoleImpl implements ObservableConsoleView, PyCodeExecutor {

  private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);
  private final ConsolePromptDecorator myPromptView;

  private PydevConsoleExecuteActionHandler myExecuteActionHandler;
  private PyConsoleSourceHighlighter mySourceHighlighter;
  private boolean myIsIPythonOutput;
  private final PyHighlighter myPyHighlighter;
  private final EditorColorsScheme myScheme;
  private boolean myHyperlink;
  private boolean myFirstRun = true;

  private XStandaloneVariablesView mySplitView;
  private ActionCallback myInitialized = new ActionCallback();

  public PythonConsoleView(Project project, String title, Sdk sdk) {
    super(project, title, PythonLanguage.getInstance());

    getVirtualFile().putUserData(LanguageLevel.KEY, PythonSdkType.getLanguageLevelForSdk(sdk));
    // Mark editor as console one, to prevent autopopup completion
    getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());
    getHistoryViewer().putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true);
    super.setPrompt(null);
    setUpdateFoldingsEnabled(false);
    //noinspection ConstantConditions
    myPyHighlighter =
      new PyHighlighter(sdk != null && sdk.getVersionString() != null ? LanguageLevel.fromPythonVersion(sdk.getVersionString()) : LanguageLevel
        .getDefault());
    myScheme = getConsoleEditor().getColorsScheme();
    PythonConsoleData data = PyConsoleUtil.getOrCreateIPythonData(getVirtualFile());
    myPromptView = new ConsolePromptDecorator(this.getConsoleEditor(), data);
  }

  public void setConsoleCommunication(ConsoleCommunication communication) {
    getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
  }

  private PyConsoleStartFolding createConsoleFolding() {
    PyConsoleStartFolding startFolding = new PyConsoleStartFolding(this);
    myExecuteActionHandler.getConsoleCommunication().addCommunicationListener(startFolding);
    getEditor().getDocument().addDocumentListener(startFolding);
    getEditor().getFoldingModel().addListener(startFolding, this);
    return startFolding;
  }

  public void addConsoleFolding(boolean isDebugConsole) {
    try {
      if (isDebugConsole && myExecuteActionHandler != null) {
        PyConsoleStartFolding folding = createConsoleFolding();
        // in debug console we should add folding from the place where the folding was turned on
        folding.setStartLineOffset(getEditor().getDocument().getTextLength());
        folding.setNumberOfCommandToStop(2);
      }
      else {
        myInitialized.doWhenDone(this::createConsoleFolding);
      }
    }
    catch (Exception e) {
      LOG.error(e.getMessage());
    }
  }

  public void setExecutionHandler(@Nonnull PydevConsoleExecuteActionHandler consoleExecuteActionHandler) {
    myExecuteActionHandler = consoleExecuteActionHandler;
  }

  public void setConsoleEnabled(boolean flag) {
    if (myExecuteActionHandler != null) {
      myExecuteActionHandler.setEnabled(flag);
    }
  }

  public void showStartMessageForFirstExecution(String startCommand) {
    if (myFirstRun && myExecuteActionHandler != null) {
      setPrompt("");
      executeStatement(startCommand + "\n", ProcessOutputTypes.SYSTEM);
      myFirstRun = false;
    }
  }


  public void inputRequested() {
    if (myExecuteActionHandler != null) {
      ConsoleCommunication consoleCommunication = myExecuteActionHandler.getConsoleCommunication();
      if (consoleCommunication instanceof PythonDebugConsoleCommunication) {
        consoleCommunication.notifyInputRequested();
      }
    }
  }

  public void inputReceived() {
    // If user's input was entered while debug console was turned off, we shouldn't wait for it anymore
    if (myExecuteActionHandler != null) {
      myExecuteActionHandler.inputReceived();
    }
  }

  @Override
  public void requestFocus() {
    IdeFocusManager.findInstance().requestFocus(getConsoleEditor().getContentComponent(), true);
  }

  @Override
  public void executeCode(final @Nonnull String code, @Nullable final Editor editor) {
    myInitialized.doWhenDone(() -> ProgressManager.getInstance().run(new Task.Backgroundable(null, "Executing Code in Console...", false) {
      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        long time = System.currentTimeMillis();
        while (!myExecuteActionHandler.isEnabled() || !myExecuteActionHandler.canExecuteNow()) {
          if (indicator.isCanceled()) {
            break;
          }
          if (System.currentTimeMillis() - time > 1000) {
            if (editor != null) {
              UIUtil.invokeLaterIfNeeded(() -> HintManager.getInstance()
                                                          .showErrorHint(editor, myExecuteActionHandler.getCantExecuteMessage()));
            }
            return;
          }
          TimeoutUtil.sleep(300);
        }
        if (!indicator.isCanceled()) {
          executeInConsole(code);
        }
      }
    }));
  }


  public void executeInConsole(String code) {
    String codeToExecute = code.endsWith("\n") ? code : code + "\n";

    String text = getConsoleEditor().getDocument().getText();
    ApplicationManager.getApplication().runWriteAction(() -> setInputText(codeToExecute));
    int oldOffset = getConsoleEditor().getCaretModel().getOffset();
    getConsoleEditor().getCaretModel().moveToOffset(codeToExecute.length());
    myExecuteActionHandler.runExecuteAction(this);

    if (!StringUtil.isEmpty(text)) {
      ApplicationManager.getApplication().runWriteAction(() -> setInputText(text));
      getConsoleEditor().getCaretModel().moveToOffset(oldOffset);
    }
  }

  public void executeStatement(@Nonnull String statement, @Nonnull Key attributes) {
    print(statement, outputTypeForAttributes(attributes));
    myExecuteActionHandler.processLine(statement);
  }

  public void printText(String text, ConsoleViewContentType outputType) {
    super.print(text, outputType);
  }

  public void print(String text, @Nonnull Key attributes) {
    print(text, outputTypeForAttributes(attributes));
  }

  @Override
  public void print(@Nonnull String text, @Nonnull ConsoleViewContentType outputType) {
    detectIPython(text, outputType);
    if (PyConsoleUtil.detectIPythonEnd(text)) {
      myIsIPythonOutput = false;
      mySourceHighlighter = null;
    }
    else if (PyConsoleUtil.detectIPythonStart(text)) {
      myIsIPythonOutput = true;
    }
    else {
      if (mySourceHighlighter == null || outputType == ConsoleViewContentType.ERROR_OUTPUT) {
        if (myHyperlink) {
          printHyperlink(text, outputType);
        }
        else {
          //Print text normally with converted attributes
          super.print(text, outputType);
        }
        myHyperlink = detectHyperlink(text);
        if (mySourceHighlighter == null && myIsIPythonOutput && PyConsoleUtil.detectSourcePrinting(text)) {
          mySourceHighlighter = new PyConsoleSourceHighlighter(this, myScheme, myPyHighlighter);
        }
      }
      else {
        try {
          mySourceHighlighter.printHighlightedSource(text);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  public void detectIPython(String text, ConsoleViewContentType outputType) {
    VirtualFile file = getVirtualFile();
    if (PyConsoleUtil.detectIPythonImported(text, outputType)) {
      PyConsoleUtil.markIPython(file);
    }
    if (PyConsoleUtil.detectIPythonAutomagicOn(text)) {
      PyConsoleUtil.setIPythonAutomagic(file, true);
    }
    if (PyConsoleUtil.detectIPythonAutomagicOff(text)) {
      PyConsoleUtil.setIPythonAutomagic(file, false);
    }
  }

  private boolean detectHyperlink(@Nonnull String text) {
    return myIsIPythonOutput && text.startsWith("File:");
  }

  private void printHyperlink(@Nonnull String text, @Nonnull ConsoleViewContentType contentType) {
    if (!StringUtil.isEmpty(text)) {
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(text.trim());

      if (vFile != null) {
        OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(getProject(), vFile, -1);

        super.printHyperlink(text, hyperlink);
      }
      else {
        super.print(text, contentType);
      }
    }
  }

  public ConsoleViewContentType outputTypeForAttributes(Key attributes) {
    ConsoleViewContentType outputType;
    if (attributes == ProcessOutputTypes.STDERR) {
      outputType = ConsoleViewContentType.ERROR_OUTPUT;
    }
    else if (attributes == ProcessOutputTypes.SYSTEM) {
      outputType = ConsoleViewContentType.SYSTEM_OUTPUT;
    }
    else {
      outputType = ConsoleViewContentType.getConsoleViewType(attributes);
    }

    return outputType;
  }

  public void setSdk(Sdk sdk) {
    getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_SDK, sdk);
  }

  public void showVariables(PydevConsoleCommunication consoleCommunication) {
    PyStackFrame stackFrame = new PyStackFrame(getProject(), consoleCommunication, new PyStackFrameInfo("", "", "", null), null);
    XStandaloneVariablesViewFactory viewFactory = getProject().getInstance(XStandaloneVariablesViewFactory.class);
    XStandaloneVariablesView view = viewFactory.create(new PyDebuggerEditorsProvider(), stackFrame);
    consoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted(boolean more) {
        view.rebuildView();
      }

      @Override
      public void inputRequested() {
      }
    });
    mySplitView = view;
    Disposer.register(this, (Disposable) view);
    splitWindow();
  }

  protected final void doAddPromptToHistory(boolean isMainPrompt) {
    flushDeferredText();
    EditorEx viewer = getHistoryViewer();
    Document document = viewer.getDocument();
    RangeHighlighter highlighter = getHistoryViewer().getMarkupModel()
                                                     .addRangeHighlighter(document.getTextLength(),
                                                                          document.getTextLength(),
                                                                          0,
                                                                          null,
                                                                          HighlighterTargetArea.EXACT_RANGE);
    String prompt;
    if (isMainPrompt) {
      prompt = myPromptView.getMainPrompt();
      print(prompt + " ", myPromptView.getPromptAttributes());
    }
    else {
      prompt = myPromptView.getIndentPrompt();
      //todo should really be myPromptView.getPromptAttributes() output type
      //but in that case flushing doesn't get handled correctly. Take a look at it later
      print(prompt + " ", ConsoleViewContentType.USER_INPUT);
    }

    highlighter.putUserData(PyConsoleCopyHandler.PROMPT_LENGTH_MARKER, prompt.length() + 1);
  }

  @Nonnull
  public String addTextRangeToHistory(@Nonnull TextRange textRange, @Nonnull EditorEx inputEditor, boolean preserveMarkup) {
    String text;
    EditorHighlighter highlighter;
    if (inputEditor instanceof EditorWindow) {
      PsiFile file = ((EditorWindow)inputEditor).getInjectedFile();
      highlighter =
        HighlighterFactory.createHighlighter(file.getVirtualFile(), EditorColorsManager.getInstance().getGlobalScheme(), getProject());
      String fullText = InjectedLanguageManager.getInstance(file.getProject()).getUnescapedText(file);
      highlighter.setText(fullText);
      text = textRange.substring(fullText);
    }
    else {
      text = inputEditor.getDocument().getText(textRange);
      highlighter = inputEditor.getHighlighter();
    }
    SyntaxHighlighter syntax =
      highlighter instanceof LexerEditorHighlighter ? ((LexerEditorHighlighter)highlighter).getSyntaxHighlighter() : null;
    doAddPromptToHistory(true);

    if (syntax != null) {
      ConsoleViewUtil.printWithHighlighting(this, text, syntax, () -> doAddPromptToHistory(false));
    }
    else {
      print(text, ConsoleViewContentType.USER_INPUT);
    }
    print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    return text;
  }


  @Nonnull
  @Override
  protected JComponent createCenterComponent() {
    //workaround for extra lines appearing in the console
    JComponent centerComponent = super.createCenterComponent();
    getHistoryViewer().getSettings().setAdditionalLinesCount(0);
    getHistoryViewer().getSettings().setUseSoftWraps(false);
    getConsoleEditor().getGutter().registerTextAnnotation(this.myPromptView);
    JComponent gutterComponentEx = getConsoleEditor().getGutterComponentEx().getComponent();
    gutterComponentEx.setBackground(TargetAWT.to(getConsoleEditor().getBackgroundColor()));
    gutterComponentEx.revalidate();
    getConsoleEditor().getColorsScheme().setColor(EditorColors.GUTTER_BACKGROUND, getConsoleEditor().getBackgroundColor());

    // settings.set
    return centerComponent;
  }


  private void splitWindow() {
    Component console = getComponent(0);
    removeAll();
    JBSplitter p = new JBSplitter(false, 2f / 3);
    p.setFirstComponent((JComponent)console);
    p.setSecondComponent(mySplitView.getComponent());
    p.setShowDividerControls(true);
    p.setHonorComponentsMinimumSize(true);

    add(p, BorderLayout.CENTER);
    validate();
    repaint();
  }

  public void restoreWindow() {
    JBSplitter pane = (JBSplitter)getComponent(0);
    removeAll();
    if (mySplitView != null) {
      Disposer.dispose((Disposable) mySplitView);
      mySplitView = null;
    }
    add(pane.getFirstComponent(), BorderLayout.CENTER);
    validate();
    repaint();
  }

  @Nullable
  @Override
  public String getPrompt() {
    if (myPromptView == null) // we're in the constructor!
    {
      return super.getPrompt();
    }
    return myPromptView.getMainPrompt();
  }


  @Override
  public void setPrompt(@Nullable String prompt) {
    if (this.myPromptView == null) // we're in the constructor!
    {
      super.setPrompt(prompt);
      return;
    }
    if (prompt != null) {
      this.myPromptView.setMainPrompt(prompt);
    }
  }


  @Override
  public void setPromptAttributes(@Nonnull ConsoleViewContentType textAttributes) {
    myPromptView.setPromptAttributes(textAttributes);
  }

  public void initialized() {
    myInitialized.setDone();
  }
}
