/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.debugger;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.*;
import com.jetbrains.python.impl.console.PythonConsoleView;
import com.jetbrains.python.impl.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.impl.debugger.containerview.PyViewNumericContainerAction;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.application.AccessToken;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.component.extension.Extensions;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.*;
import consulo.execution.debug.breakpoint.*;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.event.XDebugSessionListener;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.ide.impl.idea.remote.RemoteProcessControl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * @author yole
 */
// todo: bundle messages
// todo: pydevd supports module reloading - look for a way to use the feature
public class PyDebugProcess extends XDebugProcess implements IPyDebugProcess, ProcessListener {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.debugger.PyDebugProcess");
  private static final int CONNECTION_TIMEOUT = 60000;

  private final ProcessDebugger myDebugger;
  private final XBreakpointHandler[] myBreakpointHandlers;
  private final PyDebuggerEditorsProvider myEditorsProvider;
  private final ProcessHandler myProcessHandler;
  private final ExecutionConsole myExecutionConsole;
  private final Map<PySourcePosition, XLineBreakpoint> myRegisteredBreakpoints = new ConcurrentHashMap<>();
  private final Map<String, XBreakpoint<? extends ExceptionBreakpointProperties>> myRegisteredExceptionBreakpoints =
    new ConcurrentHashMap<>();

  private final List<PyThreadInfo> mySuspendedThreads = Collections.synchronizedList(Lists.<PyThreadInfo>newArrayList());
  private final Map<String, XValueChildrenList> myStackFrameCache = Maps.newHashMap();
  private final Map<String, PyDebugValue> myNewVariableValue = Maps.newHashMap();
  private boolean myDownloadSources = false;

  private boolean myClosing = false;

  private PyPositionConverter myPositionConverter;
  private final XSmartStepIntoHandler<?> mySmartStepIntoHandler;
  private boolean myWaitingForConnection = false;
  private PyStackFrame myConsoleContextFrame = null;
  private PyReferrersLoader myReferrersProvider;

  public PyDebugProcess(@Nonnull XDebugSession session,
                        @Nonnull ServerSocket serverSocket,
                        @Nonnull ExecutionConsole executionConsole,
                        @Nullable ProcessHandler processHandler,
                        boolean
                          multiProcess) {
    this(session,
         multiProcess ? process -> process.createMultiprocessDebugger(serverSocket) : process -> new RemoteDebugger(process,
                                                                                                                    serverSocket,
                                                                                                                    process.getConnectTimeout()),
         executionConsole,
         processHandler);
  }

  public PyDebugProcess(final @Nonnull XDebugSession session,
                        @Nonnull final ExecutionConsole executionConsole,
                        @Nullable final ProcessHandler processHandler,
                        @Nonnull String serverHost,
                        int serverPort) {
    this(session, process -> new ClientModeMultiProcessDebugger(process, serverHost, serverPort), executionConsole, processHandler);
  }

  private PyDebugProcess(@Nonnull XDebugSession session,
                         @Nonnull DebuggerFactory debuggerFactory,
                         @Nonnull ExecutionConsole executionConsole,
                         @Nullable ProcessHandler processHandler) {
    super(session);

    session.setPauseActionSupported(true);

    myDebugger = debuggerFactory.createDebugger(this);

    List<XBreakpointHandler> breakpointHandlers = new ArrayList<>();
    breakpointHandlers.add(new PyLineBreakpointHandler(this));
    breakpointHandlers.add(new PyExceptionBreakpointHandler(this));
    for (PyBreakpointHandlerFactory factory : PyBreakpointHandlerFactory.EP_NAME.getExtensionList()) {
      breakpointHandlers.add(factory.createBreakpointHandler(this));
    }
    myBreakpointHandlers = breakpointHandlers.toArray(new XBreakpointHandler[breakpointHandlers.size()]);

    myEditorsProvider = new PyDebuggerEditorsProvider();
    mySmartStepIntoHandler = new PySmartStepIntoHandler(this);
    myProcessHandler = processHandler;
    myExecutionConsole = executionConsole;
    if (myProcessHandler != null) {
      myProcessHandler.addProcessListener(this);
    }
    if (processHandler instanceof PositionConverterProvider) {
      myPositionConverter = ((PositionConverterProvider)processHandler).createPositionConverter(this);
    }
    else {
      myPositionConverter = new PyLocalPositionConverter();
    }
    myDebugger.addCloseListener(new RemoteDebuggerCloseListener() {
      @Override
      public void closed() {
        handleStop();
      }

      @Override
      public void communicationError() {
        detachDebuggedProcess();
      }

      @Override
      public void detached() {
        detachDebuggedProcess();
      }
    });

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void stackFrameChanged() {
        String currentFrameThreadId = null;
        final XStackFrame currentFrame = session.getCurrentStackFrame();
        if (currentFrame instanceof PyStackFrame) {
          currentFrameThreadId = ((PyStackFrame)currentFrame).getThreadId();
        }
        final XExecutionStack activeStack = session.getSuspendContext().getActiveExecutionStack();
        if ((activeStack == null) || (currentFrameThreadId == null)) {
          return;
        }
        final XStackFrame frameFromSuspendContext = activeStack.getTopFrame();
        String activeStackThreadId = null;
        if (frameFromSuspendContext instanceof PyStackFrame) {
          activeStackThreadId = ((PyStackFrame)frameFromSuspendContext).getThreadId();
        }
        if (!currentFrameThreadId.equals(activeStackThreadId)) {
          // another thread was selected, we should update suspendContext
          PyThreadInfo threadInfo = null;
          for (PyThreadInfo info : mySuspendedThreads) {
            if (info.getId().equals(currentFrameThreadId)) {
              threadInfo = info;
              break;
            }
          }
          if (threadInfo != null) {
            getSession().positionReached(createSuspendContext(threadInfo));
          }
        }
      }
    });
  }

  private MultiProcessDebugger createMultiprocessDebugger(ServerSocket serverSocket) {
    MultiProcessDebugger debugger = new MultiProcessDebugger(this, serverSocket, 10000);
    debugger.addOtherDebuggerCloseListener(new MultiProcessDebugger.DebuggerProcessListener() {
      @Override
      public void threadsClosed(Set<String> threadIds) {
        for (PyThreadInfo t : mySuspendedThreads) {
          if (threadIds.contains(t.getId())) {
            if (getSession().isSuspended()) {
              getSession().resume();
              break;
            }
          }
        }
      }
    });
    return debugger;
  }

  protected void detachDebuggedProcess() {
    handleStop(); //in case of normal debug we stop the session
  }

  protected void handleStop() {
    getSession().stop();
  }

  public void setPositionConverter(PyPositionConverter positionConverter) {
    myPositionConverter = positionConverter;
  }


  @Override
  public PyPositionConverter getPositionConverter() {
    return myPositionConverter;
  }

  @Nonnull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  @Override
  @Nonnull
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @Override
  @Nullable
  protected ProcessHandler doGetProcessHandler() {
    return myProcessHandler;
  }

  @Override
  @Nonnull
  public ExecutionConsole createConsole() {
    return myExecutionConsole;
  }

  @Override
  public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @Override
  public void sessionInitialized() {
    waitForConnection(getConnectionMessage(), getConnectionTitle());
  }

  protected void waitForConnection(final String connectionMessage, String connectionTitle) {
    ProgressManager.getInstance().run(new Task.Backgroundable(getSession().getProject(), connectionTitle, false) {
      @Override
      public void run(@Nonnull final ProgressIndicator indicator) {
        indicator.setText(connectionMessage);
        try {
          beforeConnect();
          myWaitingForConnection = true;
          myDebugger.waitForConnect();
          myWaitingForConnection = false;
          afterConnect();

          handshake();
          init();
          myDebugger.run();
        }
        catch (final Exception e) {
          myWaitingForConnection = false;
          if (myProcessHandler != null) {
            myProcessHandler.destroyProcess();
          }
          if (!myClosing) {
            invokeLater(() -> Messages.showErrorDialog("Unable to establish connection with debugger:\n" + e.getMessage(),
                                                       getConnectionTitle()));
          }
        }
      }
    });
  }

  @Override
  public void init() {
    getSession().rebuildViews();
    registerBreakpoints();
    setShowReturnValues(PyDebuggerSettings.getInstance().isWatchReturnValues());
  }

  @Override
  public int handleDebugPort(int localPort) throws IOException {
    if (myProcessHandler instanceof consulo.ide.impl.idea.remote.RemoteProcessControl) {
      return getRemoteTunneledPort(localPort, (consulo.ide.impl.idea.remote.RemoteProcessControl)myProcessHandler);
    }
    else {
      return localPort;
    }
  }

  protected static int getRemoteTunneledPort(int localPort, @Nonnull RemoteProcessControl handler) throws IOException {
    try {
      return handler.getRemoteSocket(localPort).getSecond();
    }
    catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public void recordSignature(PySignature signature) {
    PySignatureCacheManager.getInstance(getSession().getProject()).recordSignature(myPositionConverter.convertSignature(signature));
  }

  @Override
  public void recordLogEvent(PyConcurrencyEvent event) {
    PyConcurrencyService.getInstance(getSession().getProject()).recordEvent(getSession(), event, event.isAsyncio());
  }

  @Override
  public void showConsole(PyThreadInfo thread) {
    myConsoleContextFrame = new PyExecutionStack(this, thread).getTopFrame();
    if (myExecutionConsole instanceof PythonDebugLanguageConsoleView) {
      PythonDebugLanguageConsoleView consoleView = (PythonDebugLanguageConsoleView)myExecutionConsole;
      UIUtil.invokeLaterIfNeeded(() -> {
        consoleView.enableConsole(false);
        consoleView.getPydevConsoleView().setConsoleEnabled(true);
      });
    }
  }

  @Override
  public void consoleInputRequested(boolean isStarted) {
    if (myExecutionConsole instanceof PythonDebugLanguageConsoleView) {
      PythonConsoleView consoleView = ((PythonDebugLanguageConsoleView)myExecutionConsole).getPydevConsoleView();
      if (isStarted) {
        consoleView.inputRequested();
      }
      else {
        consoleView.inputReceived();
      }
    }
  }

  protected void afterConnect() {
  }

  protected void beforeConnect() {
  }

  protected String getConnectionMessage() {
    return "Waiting for connection...";
  }

  protected String getConnectionTitle() {
    return "Connecting To Debugger";
  }

  private void handshake() throws PyDebuggerException {
    String remoteVersion = myDebugger.handshake();
    String currentBuild = Application.get().getVersion().toString();
    if ("@@BUILD_NUMBER@@".equals(remoteVersion)) {
      remoteVersion = currentBuild;
    }
    else if (remoteVersion.startsWith("PY-")) {
      remoteVersion = remoteVersion.substring(3);
    }
    else {
      remoteVersion = null;
    }
    printToConsole("Connected to pydev debugger (build " + remoteVersion + ")\n", ConsoleViewContentType.SYSTEM_OUTPUT);

    if (remoteVersion != null) {
      if (!(remoteVersion.equals(currentBuild) || remoteVersion.startsWith(currentBuild))) {
        LOG.warn(String.format("Wrong debugger version. Remote version: %s Current build: %s", remoteVersion, currentBuild));
        printToConsole("Warning: wrong debugger version. Use pycharm-debugger.egg from PyCharm installation folder.\n",
                       ConsoleViewContentType.ERROR_OUTPUT);
      }
    }
  }

  @Override
  public void printToConsole(String text, ConsoleViewContentType contentType) {
    ((ConsoleView)myExecutionConsole).print(text, contentType);
  }

  private void registerBreakpoints() {
    registerLineBreakpoints();
    registerExceptionBreakpoints();
  }

  private void registerExceptionBreakpoints() {
    for (XBreakpoint<? extends ExceptionBreakpointProperties> bp : myRegisteredExceptionBreakpoints.values()) {
      addExceptionBreakpoint(bp);
    }
  }

  public void registerLineBreakpoints() {
    for (Map.Entry<PySourcePosition, XLineBreakpoint> entry : myRegisteredBreakpoints.entrySet()) {
      addBreakpoint(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void registerAdditionalActions(@Nonnull DefaultActionGroup leftToolbar,
                                        @Nonnull DefaultActionGroup topToolbar,
                                        @Nonnull DefaultActionGroup settings) {
    super.registerAdditionalActions(leftToolbar, topToolbar, settings);
    settings.add(new WatchReturnValuesAction(this));
    settings.add(new SimplifiedView(this));
  }

  private static class WatchReturnValuesAction extends ToggleAction {
    private volatile boolean myWatchesReturnValues;
    private final PyDebugProcess myProcess;
    private final String myText;

    public WatchReturnValuesAction(@Nonnull PyDebugProcess debugProcess) {
      super("", "Enables watching executed functions return values", null);
      myWatchesReturnValues = PyDebuggerSettings.getInstance().isWatchReturnValues();
      myProcess = debugProcess;
      myText = "Show Return Values";
    }

    @Override
    public void update(@Nonnull final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(true);
      presentation.setText(myText);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myWatchesReturnValues;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean watch) {
      myWatchesReturnValues = watch;
      PyDebuggerSettings.getInstance().setWatchReturnValues(watch);
      final Project project = e.getData(Project.KEY);
      if (project != null) {
        myProcess.setShowReturnValues(myWatchesReturnValues);
        myProcess.getSession().rebuildViews();
      }
    }
  }

  private static class SimplifiedView extends ToggleAction {
    private volatile boolean mySimplifiedView;
    private final PyDebugProcess myProcess;
    private final String myText;

    public SimplifiedView(@Nonnull PyDebugProcess debugProcess) {
      super("", "Disables watching classes, functions and modules objects", null);
      mySimplifiedView = PyDebuggerSettings.getInstance().isSimplifiedView();
      myProcess = debugProcess;
      myText = "Simplified Variables View";
    }

    @Override
    public void update(@Nonnull final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(true);
      presentation.setText(myText);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySimplifiedView;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean hide) {
      mySimplifiedView = hide;
      PyDebuggerSettings.getInstance().setSimplifiedView(hide);
      myProcess.getSession().rebuildViews();
    }
  }

  public void setShowReturnValues(boolean showReturnValues) {
    myDebugger.setShowReturnValues(showReturnValues);
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_OVER);
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_INTO);
  }

  public void startStepIntoMyCode(@Nullable XSuspendContext context) {
    if (!checkCanPerformCommands()) {
      return;
    }
    getSession().sessionResumed();
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_INTO_MY_CODE);
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    passToCurrentThread(context, ResumeOrStepCommand.Mode.STEP_OUT);
  }

  public void startSmartStepInto(String functionName) {
    dropFrameCaches();
    if (isConnected()) {
      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        myDebugger.smartStepInto(suspendedThread.getId(), functionName);
      }
    }
  }

  @Override
  public void stop() {
    myDebugger.close();
  }

  @Override
  public void resume(@Nullable XSuspendContext context) {
    passToAllThreads(ResumeOrStepCommand.Mode.RESUME);
  }

  @Override
  public void startPausing() {
    if (isConnected()) {
      myDebugger.suspendAllThreads();
    }
  }

  public void suspendAllOtherThreads(PyThreadInfo thread) {
    myDebugger.suspendOtherThreads(thread);
  }

  /**
   * Check if there is the thread suspended on the breakpoint with "Suspend all" policy
   *
   * @return true if this thread exists
   */
  @Override
  public boolean isSuspendedOnAllThreadsPolicy() {
    if (getSession().isSuspended()) {
      for (PyThreadInfo threadInfo : getThreads()) {
        final List<PyStackFrameInfo> frames = threadInfo.getFrames();
        if ((threadInfo.getState() == PyThreadInfo.State.SUSPENDED) && (frames != null)) {
          XBreakpoint<?> breakpoint = null;
          if (threadInfo.isStopOnBreakpoint()) {
            final PySourcePosition position = frames.get(0).getPosition();
            breakpoint = myRegisteredBreakpoints.get(position);
          }
          else if (threadInfo.isExceptionBreak()) {
            String exceptionName = threadInfo.getMessage();
            if (exceptionName != null) {
              breakpoint = myRegisteredExceptionBreakpoints.get(exceptionName);
            }
          }
          if ((breakpoint != null) && (breakpoint.getType().isSuspendThreadSupported()) &&
            (breakpoint.getSuspendPolicy() == SuspendPolicy.ALL)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void passToAllThreads(final ResumeOrStepCommand.Mode mode) {
    dropFrameCaches();
    if (isConnected()) {
      for (PyThreadInfo thread : myDebugger.getThreads()) {
        myDebugger.resumeOrStep(thread.getId(), mode);
      }
    }
  }

  private void passToCurrentThread(@Nullable XSuspendContext context, final ResumeOrStepCommand.Mode mode) {
    dropFrameCaches();
    if (isConnected()) {
      String threadId = threadIdBeforeResumeOrStep(context);

      for (PyThreadInfo suspendedThread : mySuspendedThreads) {
        if (threadId == null || threadId.equals(suspendedThread.getId())) {
          myDebugger.resumeOrStep(suspendedThread.getId(), mode);
          break;
        }
      }
    }
  }

  @Nullable
  private static String threadIdBeforeResumeOrStep(@Nullable XSuspendContext context) {
    if (context instanceof PySuspendContext) {
      return ((PySuspendContext)context).getActiveExecutionStack().getThreadId();
    }
    else {
      return null;
    }
  }

  protected boolean isConnected() {
    return myDebugger.isConnected();
  }

  protected void disconnect() {
    myDebugger.disconnect();
    cleanUp();
  }

  public boolean isDownloadSources() {
    return myDownloadSources;
  }

  public void setDownloadSources(boolean downloadSources) {
    myDownloadSources = downloadSources;
  }

  protected void cleanUp() {
    mySuspendedThreads.clear();
    myDownloadSources = false;
  }

  @Override
  public void runToPosition(@Nonnull final XSourcePosition position, @Nullable XSuspendContext context) {
    dropFrameCaches();
    if (isConnected() && !mySuspendedThreads.isEmpty()) {
      final PySourcePosition pyPosition = myPositionConverter.convertToPython(position);
      String type = PyLineBreakpointType.ID;
      AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
        if (document != null) {
          for (XBreakpointType breakpointType : Extensions.getExtensions(XBreakpointType.EXTENSION_POINT_NAME)) {
            if (breakpointType instanceof PyBreakpointType && ((PyBreakpointType)breakpointType).canPutInDocument(getSession().getProject(),
                                                                                                                  document)) {
              type = breakpointType.getId();
              break;
            }
          }
        }
      }
      finally {
        lock.finish();
      }
      myDebugger.setTempBreakpoint(type, pyPosition.getFile(), pyPosition.getLine());

      passToCurrentThread(context, ResumeOrStepCommand.Mode.RESUME);
    }
  }

  @Override
  public PyDebugValue evaluate(final String expression, final boolean execute, boolean doTrunc) throws PyDebuggerException {
    dropFrameCaches();
    final PyStackFrame frame = currentFrame();
    return evaluate(expression, execute, frame, doTrunc);
  }

  private PyDebugValue evaluate(String expression, boolean execute, PyStackFrame frame, boolean trimResult) throws PyDebuggerException {
    return myDebugger.evaluate(frame.getThreadId(), frame.getFrameId(), expression, execute, trimResult);
  }

  public void consoleExec(String command, PyDebugCallback<String> callback) {
    dropFrameCaches();
    try {
      final PyStackFrame frame = currentFrame();
      myDebugger.consoleExec(frame.getThreadId(), frame.getFrameId(), command, callback);
    }
    catch (PyDebuggerException e) {
      callback.error(e);
    }
  }

  @Override
  @Nullable
  public XValueChildrenList loadFrame() throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    //do not reload frame every time it is needed, because due to bug in pdb, reloading frame clears all variable changes
    if (!myStackFrameCache.containsKey(frame.getThreadFrameId())) {
      XValueChildrenList values = myDebugger.loadFrame(frame.getThreadId(), frame.getFrameId());
      myStackFrameCache.put(frame.getThreadFrameId(), values);
    }
    return applyNewValue(myStackFrameCache.get(frame.getThreadFrameId()), frame.getThreadFrameId());
  }

  private XValueChildrenList applyNewValue(XValueChildrenList pyDebugValues, String threadFrameId) {
    if (myNewVariableValue.containsKey(threadFrameId)) {
      PyDebugValue newValue = myNewVariableValue.get(threadFrameId);
      XValueChildrenList res = new XValueChildrenList();
      for (int i = 0; i < pyDebugValues.size(); i++) {
        final String name = pyDebugValues.getName(i);
        if (name.equals(newValue.getName())) {
          res.add(name, newValue);
        }
        else {
          res.add(name, pyDebugValues.getValue(i));
        }
      }
      return res;
    }
    else {
      return pyDebugValues;
    }
  }

  @Override
  public XValueChildrenList loadVariable(final PyDebugValue var) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    PyDebugValue debugValue = var.setName(var.getFullName());
    return myDebugger.loadVariable(frame.getThreadId(), frame.getFrameId(), debugValue);
  }

  @Override
  public void loadReferrers(PyReferringObjectsValue var, PyDebugCallback<XValueChildrenList> callback) {
    try {
      final PyStackFrame frame = currentFrame();
      myDebugger.loadReferrers(frame.getThreadId(), frame.getFrameId(), var, callback);
    }
    catch (PyDebuggerException e) {
      callback.error(e);
    }
  }

  @Override
  public void changeVariable(final PyDebugValue var, final String value) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    PyDebugValue newValue = myDebugger.changeVariable(frame.getThreadId(), frame.getFrameId(), var, value);
    myNewVariableValue.put(frame.getThreadFrameId(), newValue);
  }

  @Nullable
  @Override
  public PyReferrersLoader getReferrersLoader() {
    if (myReferrersProvider == null) {
      myReferrersProvider = new PyReferrersLoader(this);
    }
    return myReferrersProvider;
  }

  @Override
  public ArrayChunk getArrayItems(PyDebugValue var,
                                  int rowOffset,
                                  int colOffset,
                                  int rows,
                                  int cols,
                                  String format) throws PyDebuggerException {
    final PyStackFrame frame = currentFrame();
    return myDebugger.loadArrayItems(frame.getThreadId(), frame.getFrameId(), var, rowOffset, colOffset, rows, cols, format);
  }

  @Nullable
  public String loadSource(String path) {
    return myDebugger.loadSource(path);
  }

  @Override
  public boolean canSaveToTemp(String name) {
    final Project project = getSession().getProject();
    return PyDebugSupportUtils.canSaveToTemp(project, name);
  }

  @Nonnull
  private PyStackFrame currentFrame() throws PyDebuggerException {
    if (!isConnected()) {
      throw new PyDebuggerException("Disconnected");
    }

    final PyStackFrame frame = (PyStackFrame)getSession().getCurrentStackFrame();

    if (frame == null && myConsoleContextFrame != null) {
      return myConsoleContextFrame;
    }

    if (frame == null) {
      throw new PyDebuggerException("Process is running");
    }

    return frame;
  }

  @Nullable
  private String getFunctionName(final XLineBreakpoint breakpoint) {
    if (breakpoint.getSourcePosition() == null) {
      return null;
    }
    final VirtualFile file = breakpoint.getSourcePosition().getFile();
    AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      final Project project = getSession().getProject();
      if (document != null) {
        if (file.getFileType() == PythonFileType.INSTANCE) {
          PsiElement psiElement = XDebuggerUtil.getInstance().
            findContextElement(file, document.getLineStartOffset(breakpoint.getSourcePosition().getLine()), project, false);
          PyFunction function = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class);
          if (function != null) {
            return function.getName();
          }
        }
      }
      return null;
    }
    finally {
      lock.finish();
    }
  }

  public void addBreakpoint(final PySourcePosition position, final XLineBreakpoint breakpoint) {
    myRegisteredBreakpoints.put(position, breakpoint);
    if (isConnected()) {
      final String conditionExpression =
        breakpoint.getConditionExpression() == null ? null : breakpoint.getConditionExpression().getExpression();
      final String logExpression = breakpoint.getLogExpressionObject() == null ? null : breakpoint.getLogExpressionObject().getExpression();
      SuspendPolicy policy = breakpoint.getType().isSuspendThreadSupported() ? breakpoint.getSuspendPolicy() : SuspendPolicy.NONE;
      myDebugger.setBreakpoint(breakpoint.getType().getId(),
                               position.getFile(),
                               position.getLine(),
                               conditionExpression,
                               logExpression,
                               getFunctionName(breakpoint),
                               policy);
    }
  }

  public void addTemporaryBreakpoint(String typeId, String file, int line) {
    if (isConnected()) {
      myDebugger.setTempBreakpoint(typeId, file, line);
    }
  }

  public void removeBreakpoint(final PySourcePosition position) {
    XLineBreakpoint breakpoint = myRegisteredBreakpoints.get(position);
    if (breakpoint != null) {
      myRegisteredBreakpoints.remove(position);
      if (isConnected()) {
        myDebugger.removeBreakpoint(breakpoint.getType().getId(), position.getFile(), position.getLine());
      }
    }
  }

  public void addExceptionBreakpoint(XBreakpoint<? extends ExceptionBreakpointProperties> breakpoint) {
    myRegisteredExceptionBreakpoints.put(breakpoint.getProperties().getException(), breakpoint);
    if (isConnected()) {
      myDebugger.addExceptionBreakpoint(breakpoint.getProperties());
    }
  }

  public void removeExceptionBreakpoint(XBreakpoint<? extends ExceptionBreakpointProperties> breakpoint) {
    myRegisteredExceptionBreakpoints.remove(breakpoint.getProperties().getException());
    if (isConnected()) {
      myDebugger.removeExceptionBreakpoint(breakpoint.getProperties());
    }
  }

  public Collection<PyThreadInfo> getThreads() {
    return myDebugger.getThreads();
  }

  @Override
  public void threadSuspended(final PyThreadInfo threadInfo, boolean updateSourcePosition) {
    if (!mySuspendedThreads.contains(threadInfo)) {
      mySuspendedThreads.add(threadInfo);

      final List<PyStackFrameInfo> frames = threadInfo.getFrames();
      if (frames != null) {
        final PySuspendContext suspendContext = createSuspendContext(threadInfo);

        XBreakpoint<?> breakpoint = null;
        if (threadInfo.isStopOnBreakpoint()) {
          final PySourcePosition position = frames.get(0).getPosition();
          breakpoint = myRegisteredBreakpoints.get(position);
          if (breakpoint == null) {
            myDebugger.removeTempBreakpoint(position.getFile(), position.getLine());
          }
        }
        else if (threadInfo.isExceptionBreak()) {
          String exceptionName = threadInfo.getMessage();
          threadInfo.setMessage(null);
          if (exceptionName != null) {
            breakpoint = myRegisteredExceptionBreakpoints.get(exceptionName);
          }
        }
        if (breakpoint != null) {
          if ((breakpoint.getType().isSuspendThreadSupported()) && (breakpoint.getSuspendPolicy() == SuspendPolicy.ALL)) {
            suspendAllOtherThreads(threadInfo);
          }
        }

        if (updateSourcePosition) {
          if (breakpoint != null) {
            if (!getSession().breakpointReached(breakpoint, threadInfo.getMessage(), suspendContext)) {
              resume(suspendContext);
            }
          }
          else {
            getSession().positionReached(suspendContext);
          }
        }
      }
    }
  }

  @Nonnull
  protected PySuspendContext createSuspendContext(PyThreadInfo threadInfo) {
    return new PySuspendContext(this, threadInfo);
  }

  @Override
  public void threadResumed(final PyThreadInfo threadInfo) {
    mySuspendedThreads.remove(threadInfo);
  }

  private void dropFrameCaches() {
    myStackFrameCache.clear();
    myNewVariableValue.clear();
  }

  @Nonnull
  public List<PydevCompletionVariant> getCompletions(String prefix) throws Exception {
    if (isConnected()) {
      dropFrameCaches();
      final PyStackFrame frame = currentFrame();
      return myDebugger.getCompletions(frame.getThreadId(), frame.getFrameId(), prefix);
    }
    return Lists.newArrayList();
  }

  @Nonnull
  public String getDescription(String prefix) throws Exception {
    if (isConnected()) {
      dropFrameCaches();
      final PyStackFrame frame = currentFrame();
      return myDebugger.getDescription(frame.getThreadId(), frame.getFrameId(), prefix);
    }
    return "";
  }


  @Override
  public void startNotified(ProcessEvent event) {
  }

  @Override
  public void processTerminated(ProcessEvent event) {
    myDebugger.close();
  }

  @Override
  public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
    myClosing = true;
  }

  @Override
  public void onTextAvailable(ProcessEvent event, Key outputType) {
  }

  public PyStackFrame createStackFrame(PyStackFrameInfo frameInfo) {
    return new PyStackFrame(getSession().getProject(), this, frameInfo, getPositionConverter().convertFromPython(frameInfo.getPosition()));
  }

  @Override
  public String getCurrentStateMessage() {
    if (getSession().isStopped()) {
      return XDebuggerBundle.message("debugger.state.message.disconnected");
    }
    else if (isConnected()) {
      return XDebuggerBundle.message("debugger.state.message.connected");
    }
    else {
      return getConnectionMessage();
    }
  }

  public void addProcessListener(ProcessListener listener) {
    ProcessHandler handler = doGetProcessHandler();
    if (handler != null) {
      handler.addProcessListener(listener);
    }
  }

  public boolean isWaitingForConnection() {
    return myWaitingForConnection;
  }

  public void setWaitingForConnection(boolean waitingForConnection) {
    myWaitingForConnection = waitingForConnection;
  }

  public int getConnectTimeout() {
    return CONNECTION_TIMEOUT;
  }


  @Nullable
  private XSourcePosition getCurrentFrameSourcePosition() {
    try {
      PyStackFrame frame = currentFrame();

      return frame.getSourcePosition();
    }
    catch (PyDebuggerException e) {
      return null;
    }
  }

  public Project getProject() {
    return getSession().getProject();
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePositionForName(String name, String parentType) {
    if (name == null) {
      return null;
    }
    XSourcePosition currentPosition = getCurrentFrameSourcePosition();

    final PsiFile file = getPsiFile(currentPosition);

    if (file == null) {
      return null;
    }

    if (Strings.isNullOrEmpty(parentType)) {
      final Ref<PsiElement> elementRef = resolveInCurrentFrame(name, currentPosition, file);
      return elementRef.isNull() ? null : XDebuggerUtil.getInstance().createPositionByElement(elementRef.get());
    }
    else {
      final PyType parentDef = resolveTypeFromString(parentType, file);
      if (parentDef == null) {
        return null;
      }
      List<? extends RatedResolveResult> results =
        parentDef.resolveMember(name, null, AccessDirection.READ, PyResolveContext.noImplicits());
      if (results != null && !results.isEmpty()) {
        return XDebuggerUtil.getInstance().createPositionByElement(results.get(0).getElement());
      }
      else {
        return typeToPosition(parentDef); // at least try to return parent
      }
    }
  }


  @Nonnull
  private static Ref<PsiElement> resolveInCurrentFrame(final String name, XSourcePosition currentPosition, PsiFile file) {
    final Ref<PsiElement> elementRef = Ref.create();
    PsiElement currentElement = file.findElementAt(currentPosition.getOffset());

    if (currentElement == null) {
      return elementRef;
    }

    PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
        if ((element instanceof PyImportElement)) {
          PyImportElement importElement = (PyImportElement)element;
          if (name.equals(importElement.getVisibleName())) {
            if (elementRef.isNull()) {
              elementRef.set(element);
            }
            return false;
          }
          return true;
        }
        else {
          if (elementRef.isNull()) {
            elementRef.set(element);
          }
          return false;
        }
      }

      @Nullable
      @Override
      public <T> T getHint(@Nonnull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(@Nonnull Event event, @Nullable Object associated) {

      }
    }, currentElement, name, null);
    return elementRef;
  }

  @Nullable
  private PsiFile getPsiFile(XSourcePosition currentPosition) {
    if (currentPosition == null) {
      return null;
    }

    return PsiManager.getInstance(getProject()).findFile(currentPosition.getFile());
  }


  @Nullable
  @Override
  public XSourcePosition getSourcePositionForType(String typeName) {
    XSourcePosition currentPosition = getCurrentFrameSourcePosition();

    final PsiFile file = getPsiFile(currentPosition);

    if (file == null || typeName == null || !(file instanceof PyFile)) {
      return null;
    }


    final PyType pyType = resolveTypeFromString(typeName, file);
    return pyType == null ? null : typeToPosition(pyType);
  }

  @Override
  public void showNumericContainer(PyDebugValue value) {
    PyViewNumericContainerAction.showNumericViewer(getProject(), value);
  }

  @Nullable
  private static XSourcePosition typeToPosition(PyType pyType) {
    final PyClassType classType = PyUtil.as(pyType, PyClassType.class);

    if (classType != null) {
      return XDebuggerUtil.getInstance().createPositionByElement(classType.getPyClass());
    }

    final PyModuleType moduleType = PyUtil.as(pyType, PyModuleType.class);
    if (moduleType != null) {
      return XDebuggerUtil.getInstance().createPositionByElement(moduleType.getModule());
    }
    return null;
  }

  private PyType resolveTypeFromString(String typeName, PsiFile file) {
    typeName = typeName.replace("__builtin__.", "");
    PyType pyType = null;
    if (!typeName.contains(".")) {

      pyType = PyTypeParser.getTypeByName(file, typeName);
    }
    if (pyType == null) {
      PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
      PyPsiFacade psiFacade = PyPsiFacade.getInstance(getProject());
      PsiFile dummyFile = generator.createDummyFile(((PyFile)file).getLanguageLevel(), "");
      Module moduleForFile = ModuleUtilCore.findModuleForPsiElement(file);
      dummyFile.putUserData(ModuleUtilCore.KEY_MODULE, moduleForFile);

      pyType = psiFacade.parseTypeAnnotation(typeName, dummyFile);
    }
    return pyType;
  }

  private interface DebuggerFactory {
    @Nonnull
    ProcessDebugger createDebugger(@Nonnull PyDebugProcess process);
  }
}
