// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev.transport;

import consulo.application.Application;
import consulo.logging.Logger;
import consulo.util.lang.TimeoutUtil;
import consulo.process.io.BaseOutputReader;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.Future;

/**
 * @author Alexander Koshevoy
 */
public abstract class BaseDebuggerReader extends BaseOutputReader {
    private static final Logger LOG = Logger.getInstance(BaseDebuggerReader.class);

    private final RemoteDebugger myDebugger;
    private StringBuilder myTextBuilder = new StringBuilder();

    public BaseDebuggerReader(InputStream inputStream, Charset charset, RemoteDebugger debugger) {
        super(inputStream, charset);
        myDebugger = debugger;
    }

    protected RemoteDebugger getDebugger() {
        return myDebugger;
    }

    @Override
    protected void doRun() {
        try {
            while (true) {
                boolean read = readAvailableBlocking();

                if (!read) {
                    break;
                }
                else {
                    if (isStopped) {
                        break;
                    }

                    TimeoutUtil.sleep(mySleepingPolicy.getTimeToSleep(true));
                }
            }
        }
        catch (Exception e) {
            onCommunicationError();
        }
        finally {
            close();
            onExit();
        }
    }

    protected abstract void onExit();

    protected abstract void onCommunicationError();

    @Override
    protected Future<?> executeOnPooledThread(Runnable runnable) {
        return Application.get().executeOnPooledThread(runnable);
    }

    @Override
    protected void close() {
        try {
            super.close();
        }
        catch (IOException e) {
            LOG.error(e);
        }
    }

    @Override
    public void stop() {
        super.stop();
        close();
    }

    @Override
    protected void onTextAvailable(String text) {
        myTextBuilder.append(text);
        if (text.contains("\n")) {
            String[] lines = myTextBuilder.toString().split("\n");
            myTextBuilder = new StringBuilder();

            if (!text.endsWith("\n")) {
                myTextBuilder.append(lines[lines.length - 1]);
                lines = Arrays.copyOf(lines, lines.length - 1);
            }

            for (String line : lines) {
                myDebugger.processResponse(line + "\n");
            }
        }
    }
}
