// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.protocol.PythonConsoleBackendService;
import com.jetbrains.python.console.protocol.PythonConsoleFrontendService;
import com.jetbrains.python.console.transport.server.ServerClosedException;
import com.jetbrains.python.console.transport.server.TNettyServer;
import com.jetbrains.python.console.transport.server.TNettyServerTransport;
import com.jetbrains.python.debugger.PyDebugValueExecutionService;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.PyFrameListener;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.component.ProcessCanceledException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransport;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class PydevConsoleCommunicationServer extends PydevConsoleCommunication {
    static final Logger LOG = Logger.getInstance(PydevConsoleCommunicationServer.class);

    private final TNettyServerTransport myServerTransport;

    /**
     * This is the myServer responsible for giving input to a raw_input() requested.
     */
    private final TNettyServer myServer;

    /**
     * Thrift RPC client for sending messages to the myServer.
     */
    private @Nullable PythonConsoleBackendServiceDisposable myClient = null;

    private final Lock myStateLock = new ReentrantLock();
    private final Condition myStateChanged = myStateLock.newCondition();

    /**
     * Initial non-thread safe {@link PythonConsoleBackendService.Client}.
     * <p>
     * Guarded by {@link #myStateLock}.
     */
    private PythonConsoleBackendService.@Nullable Iface myInitialPythonConsoleClient = null;

    /**
     * Guarded by {@link #myStateLock}.
     */
    private @Nullable Process myPythonConsoleProcess = null;

    /**
     * Guarded by {@link #myStateLock}.
     */
    private boolean myIsFailedOnBound = false;

    /**
     * Guarded by {@link #myStateLock}.
     */
    private boolean myIsServerBound = false;

    /**
     * Guarded by {@link #myStateLock}.
     */
    private boolean myIsClosed = false;

    PydevConsoleCommunicationServer(Project project, String host, int port) {
        super(project);
        PythonConsoleFrontendService.Iface serverHandler = createPythonConsoleFrontendHandler();
        PythonConsoleFrontendService.Processor<PythonConsoleFrontendService.Iface> serverProcessor =
            new PythonConsoleFrontendService.Processor<>(serverHandler);
        //noinspection IOResourceOpenedButNotSafelyClosed
        myServerTransport = new TNettyServerTransport(host, port);
        myServer = new TNettyServer(myServerTransport, serverProcessor);
    }

    /**
     * Must be called once.
     */
    public void serve() throws InterruptedException {
        // start server in the separate thread
        Application.get().executeOnPooledThread(() -> myServer.serve());

        Application.get().executeOnPooledThread(() -> {
            // this will wait for the connection of Python Console to the IDE
            TTransport clientTransport;
            try {
                clientTransport = myServerTransport.getReverseTransport();
            }
            catch (ServerClosedException e) {
                // this is the normal execution flow
                throw new ProcessCanceledException(e);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProcessCanceledException(e);
            }

            TBinaryProtocol clientProtocol = new TBinaryProtocol(clientTransport);
            PythonConsoleBackendService.Client backendClient = new PythonConsoleBackendService.Client(clientProtocol);

            myStateLock.lock();
            try {
                // early close
                if (myIsClosed) {
                    myServer.stop();

                    // this is the normal execution flow
                    throw new ProcessCanceledException();
                }

                myInitialPythonConsoleClient = backendClient;

                myStateChanged.signalAll();
            }
            finally {
                myStateLock.unlock();
            }

            PyDebugValueExecutionService executionService = PyDebugValueExecutionService.getInstance(myProject);
            executionService.sessionStarted(this);
            addFrameListener(new PyFrameListener() {
                @Override
                public void frameChanged() {
                    executionService.cancelSubmittedTasks(PydevConsoleCommunicationServer.this);
                }

                @Override
                public void sessionStopped(@Nullable PyFrameAccessor communication) {
                    executionService.cancelSubmittedTasks(PydevConsoleCommunicationServer.this);
                }
            });
        });

        myStateLock.lock();
        try {
            try {
                // waiting on `CountDownLatch.await()` within `myStateLock` might be harmful
                myServerTransport.waitForBind();
                myIsServerBound = true;
            }
            finally {
                myIsFailedOnBound = !myIsServerBound;

                myStateChanged.signalAll();
            }
        }
        finally {
            myStateLock.unlock();
        }
    }

    /**
     * The Python Console process is expected to be set after the myServer is
     * bound.
     */
    public void setPythonConsoleProcess(Process pythonConsoleProcess) {
        myStateLock.lock();
        try {
            if (myIsClosed) {
                throw new CommunicationClosedException();
            }

            if (!myIsServerBound) {
                LOG.warn("Python Console process is set before IDE server is bound, the process may not be able to connect to the server");
            }

            myPythonConsoleProcess = pythonConsoleProcess;

            myStateChanged.signalAll();
        }
        finally {
            myStateLock.unlock();
        }
    }

    @Override
    protected PythonConsoleBackendServiceDisposable getPythonConsoleBackendClient() {
        myStateLock.lock();
        try {
            while (!myIsClosed && !myIsFailedOnBound) {
                // if `client` is set just return it
                PythonConsoleBackendServiceDisposable currentClient = myClient;
                if (currentClient != null) {
                    return currentClient;
                }

                PythonConsoleBackendService.Iface initialPythonConsoleClient = myInitialPythonConsoleClient;
                Process pythonConsoleProcess = myPythonConsoleProcess;

                if (initialPythonConsoleClient != null && pythonConsoleProcess != null) {
                    PythonConsoleBackendServiceDisposable newClient = PythonConsoleClientUtil.synchronizedPythonConsoleClient(
                        PydevConsoleCommunication.class.getClassLoader(),
                        initialPythonConsoleClient,
                        pythonConsoleProcess
                    );
                    myClient = newClient;
                    return newClient;
                }
                else {
                    try {
                        myStateChanged.await();
                    }
                    catch (InterruptedException e) {
                        // Kotlin rethrows `InterruptedException` unchecked
                        Thread.currentThread().interrupt();
                        throw new ProcessCanceledException(e);
                    }
                }
            }
            throw new CommunicationClosedException();
        }
        finally {
            myStateLock.unlock();
        }
    }

    @Override
    protected Future<?> closeCommunication() {
        ProgressIndicator progressIndicator = ProgressIndicatorProvider.getInstance().getProgressIndicator();

        myStateLock.lock();
        try {
            try {
                myIsClosed = true;
            }
            finally {
                myStateChanged.signalAll();
            }
        }
        finally {
            myStateLock.unlock();
        }

        // if client exists then try to gracefully `close()` it
        try {
            PythonConsoleBackendServiceDisposable currentClient = myClient;
            if (currentClient != null) {
                if (progressIndicator != null) {
                    progressIndicator.setText2(PyLocalize.debuggerSendingCloseMessage());
                }

                currentClient.close();
                currentClient.dispose();
            }
        }
        catch (Exception e) {
            // ignore exceptions on `client` shutdown
        }

        Process pythonConsoleProcess = myPythonConsoleProcess;
        if (pythonConsoleProcess != null) {
            if (progressIndicator != null) {
                progressIndicator.setText2(PyLocalize.debuggerWaitingToFinish());
            }

            // TODO move under feature!
            try {
                do {
                    if (progressIndicator != null) {
                        progressIndicator.checkCanceled();
                    }
                }
                while (!pythonConsoleProcess.waitFor(500, TimeUnit.MILLISECONDS));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return myServer.stop();
    }

    @Override
    public boolean isCommunicationClosed() {
        myStateLock.lock();
        try {
            return myIsClosed;
        }
        finally {
            myStateLock.unlock();
        }
    }
}
