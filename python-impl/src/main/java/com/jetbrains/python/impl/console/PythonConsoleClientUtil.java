// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.protocol.CompletionOption;
import com.jetbrains.python.console.protocol.DebugValue;
import com.jetbrains.python.console.protocol.ExceedingArrayDimensionsException;
import com.jetbrains.python.console.protocol.GetArrayResponse;
import com.jetbrains.python.console.protocol.PythonConsoleBackendService;
import com.jetbrains.python.console.protocol.PythonTableException;
import com.jetbrains.python.console.protocol.PythonUnhandledException;
import com.jetbrains.python.console.protocol.UnsupportedArrayTypeException;
import consulo.util.concurrent.ConcurrencyUtil;
import org.apache.thrift.TException;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PythonConsoleClientUtil {
    private static final String PYTHON_CONSOLE_COMMAND_THREAD_FACTORY_NAME = "Python Console Command Executor";

    private PythonConsoleClientUtil() {
    }

    static PythonConsoleBackendServiceDisposable synchronizedPythonConsoleClient(
        ClassLoader loader,
        PythonConsoleBackendService.Iface delegate,
        Process pythonConsoleProcess
    ) {
        // DO NOT replace this ExecutorService with `SequentialTaskExecutor.createSequentialApplicationPoolExecutor()`!
        // It may use different threads for executing different tasks in a sequence and it breaks the prerequisite of `PipedInputStream`
        // that requires every read to be made from the same thread.
        ExecutorService executorService = ConcurrencyUtil.newSingleThreadExecutor(PYTHON_CONSOLE_COMMAND_THREAD_FACTORY_NAME);

        InvocationHandler handler = (proxyObject, method, args) -> {
            // we evaluate the original method in the other thread in order to control it
            Future<@Nullable Object> future =
                executorService.submit((Callable<@Nullable Object>) () -> invokeOriginalMethod(args, method, delegate));

            while (true) {
                try {
                    return future.get(10L, TimeUnit.MILLISECONDS);
                }
                catch (TimeoutException e) {
                    if (!pythonConsoleProcess.isAlive()) {
                        int exitValue = pythonConsoleProcess.exitValue();
                        throw new PyConsoleProcessFinishedException(exitValue);
                    }
                    // continue waiting for the end of the operation execution
                }
                catch (ExecutionException e) {
                    if (!pythonConsoleProcess.isAlive()) {
                        int exitValue = pythonConsoleProcess.exitValue();
                        throw new PyConsoleProcessFinishedException(exitValue);
                    }
                    Throwable cause = e.getCause();
                    throw cause != null ? cause : e;
                }
            }
        };
        PythonConsoleBackendService.Iface proxy = (PythonConsoleBackendService.Iface) Proxy.newProxyInstance(
            loader,
            new Class<?>[]{PythonConsoleBackendService.Iface.class},
            handler
        );
        // make the `proxy` disposable
        return new DisposableClient(proxy, executorService);
    }

    private static @Nullable Object invokeOriginalMethod(Object @Nullable [] args, Method method, Object delegate) throws Exception {
        try {
            if (args != null) {
                return method.invoke(delegate, args);
            }
            else {
                return method.invoke(delegate);
            }
        }
        catch (InvocationTargetException e) {
            // Kotlin `throw e.cause ?: e`: Java `Callable` may throw only `Exception`, errors are rethrown as is
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /**
     * Delegates {@link PythonConsoleBackendService.Iface} to the synchronized {@code proxy} (Kotlin {@code Iface by proxy}).
     */
    private static final class DisposableClient implements PythonConsoleBackendServiceDisposable {
        private final PythonConsoleBackendService.Iface myProxy;
        private final ExecutorService myExecutorService;

        private DisposableClient(PythonConsoleBackendService.Iface proxy, ExecutorService executorService) {
            myProxy = proxy;
            myExecutorService = executorService;
        }

        @Override
        public void dispose() {
            myExecutorService.shutdownNow();
            try {
                while (!myExecutorService.awaitTermination(1L, TimeUnit.SECONDS)) {
                    // wait for the executor to terminate
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public boolean execLine(String line) throws PythonUnhandledException, TException {
            return myProxy.execLine(line);
        }

        @Override
        public boolean execMultipleLines(String lines) throws PythonUnhandledException, TException {
            return myProxy.execMultipleLines(lines);
        }

        @Override
        public List<CompletionOption> getCompletions(String text, String actTok) throws PythonUnhandledException, TException {
            return myProxy.getCompletions(text, actTok);
        }

        @Override
        public String getDescription(String text) throws TException {
            return myProxy.getDescription(text);
        }

        @Override
        public List<DebugValue> getFrame(int type) throws PythonUnhandledException, TException {
            return myProxy.getFrame(type);
        }

        @Override
        public List<DebugValue> getVariable(String variable) throws PythonUnhandledException, TException {
            return myProxy.getVariable(variable);
        }

        @Override
        public boolean setUserTypeRenderers(String renderers) throws PythonUnhandledException, TException {
            return myProxy.setUserTypeRenderers(renderers);
        }

        @Override
        public void changeVariable(String evaluationExpression, String value) throws PythonUnhandledException, TException {
            myProxy.changeVariable(evaluationExpression, value);
        }

        @Override
        public void connectToDebugger(int localPort, String host, Map<String, Boolean> opts, Map<String, String> extraEnvs)
            throws PythonUnhandledException, TException {
            myProxy.connectToDebugger(localPort, host, opts, extraEnvs);
        }

        @Override
        public void interrupt() throws TException {
            myProxy.interrupt();
        }

        @Override
        public String handshake() throws TException {
            return myProxy.handshake();
        }

        @Override
        public void close() throws TException {
            myProxy.close();
        }

        @Override
        public List<DebugValue> evaluate(String expression, boolean doTrunc) throws PythonUnhandledException, TException {
            return myProxy.evaluate(expression, doTrunc);
        }

        @Override
        public GetArrayResponse getArray(String vars, int rowOffset, int colOffset, int rows, int cols, String format)
            throws UnsupportedArrayTypeException, ExceedingArrayDimensionsException, PythonUnhandledException, TException {
            return myProxy.getArray(vars, rowOffset, colOffset, rows, cols, format);
        }

        @Override
        public void execDataViewerAction(String varName, String action, String myArgs) throws PythonUnhandledException, TException {
            myProxy.execDataViewerAction(varName, action, myArgs);
        }

        @Override
        public void loadFullValue(int seq, List<String> variables) throws PythonUnhandledException, TException {
            myProxy.loadFullValue(seq, variables);
        }

        @Override
        public String execRaw(String command) throws PythonUnhandledException, TException {
            return myProxy.execRaw(command);
        }

        @Override
        public String execTableCommand(String tableVariable, String commandType, String startIndex, String endIndex, String format)
            throws PythonUnhandledException, PythonTableException, TException {
            return myProxy.execTableCommand(tableVariable, commandType, startIndex, endIndex, format);
        }

        @Override
        public String execTableImageCommand(String tableVariable, String commandType, String offset, String imageId)
            throws PythonUnhandledException, PythonTableException, TException {
            return myProxy.execTableImageCommand(tableVariable, commandType, offset, imageId);
        }
    }
}
