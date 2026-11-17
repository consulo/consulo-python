package com.jetbrains.python.console.transport.server;

import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerTransport;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TNettyServer {
    @FunctionalInterface
    private interface AwaitTerminationImpl {
        boolean await(long timeout, TimeUnit unit) throws InterruptedException;
    }

    private final AwaitTerminationImpl myAwaitTerminationImpl;

    private final TThreadPoolServer myServer;

    private TNettyServer(TServerTransport transport, TProcessor processor, AwaitTerminationImpl awaitTerminationImpl) {
        myAwaitTerminationImpl = awaitTerminationImpl;

        TThreadPoolServer.Args args = new TThreadPoolServer.Args(transport)
            .processor(processor)
            .protocolFactory(new TBinaryProtocol.Factory())
            .stopTimeoutVal(1);

        myServer = new TThreadPoolServer(args);
    }

    public TNettyServer(TNettyServerTransport transport, TProcessor processor) {
        this(transport, processor, transport::awaitTermination);
    }

    public void serve() {
        myServer.serve();
    }

    public Future<?> stop() {
        myServer.stop();

        return new Future<@Nullable Void>() {
            @Override
            public boolean isDone() {
                try {
                    return myAwaitTerminationImpl.await(0, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public @Nullable Void get() throws InterruptedException {
                while (!myAwaitTerminationImpl.await(1, TimeUnit.MINUTES)) {
                }
                return null;
            }

            @Override
            public @Nullable Void get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
                if (!myAwaitTerminationImpl.await(timeout, unit)) {
                    throw new TimeoutException();
                }
                else {
                    return null;
                }
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }
}
