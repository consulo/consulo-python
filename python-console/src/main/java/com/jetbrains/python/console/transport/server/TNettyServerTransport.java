package com.jetbrains.python.console.transport.server;

import com.jetbrains.python.console.transport.DirectedMessage;
import com.jetbrains.python.console.transport.DirectedMessageCodec;
import com.jetbrains.python.console.transport.DirectedMessageHandler;
import com.jetbrains.python.console.transport.TCumulativeTransport;
import consulo.logging.Logger;
import consulo.util.concurrent.ConcurrencyUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LoggingHandler;
import org.apache.thrift.TConfiguration;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TNettyServerTransport extends TServerTransport {
    public static final Logger LOG = Logger.getInstance(TNettyServerTransport.class);

    private final NettyServer myNettyServer;

    /**
     * @param host the hostname to bind Python Console server at
     * @param port the port to bind Python Console server at
     */
    public TNettyServerTransport(String host, int port) {
        myNettyServer = new NettyServer(host, port);
    }

    @Override
    public void listen() throws TTransportException {
        try {
            myNettyServer.listen();
        }
        catch (InterruptedException e) {
            throw new TTransportException(e);
        }
    }

    public void waitForBind() throws InterruptedException {
        myNettyServer.waitForBind();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return myNettyServer.awaitTermination(timeout, unit);
    }

    @Override
    public TTransport accept() throws TTransportException {
        return myNettyServer.accept();
    }

    @Override
    public void interrupt() {
        close();
    }

    @Override
    public void close() {
        myNettyServer.close();
    }

    public TTransport getReverseTransport() throws InterruptedException {
        return myNettyServer.takeReverseTransport();
    }

    private static class NettyServer {
        private final String myHost;
        private final int myPort;

        private final AtomicBoolean myClosed = new AtomicBoolean(false);

        private final BlockingQueue<TTransport> myAcceptQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<TTransport> myReverseTransportQueue = new LinkedBlockingQueue<>();

        // The first one, often called 'boss', accepts an incoming connection.
        private final NioEventLoopGroup myBossGroup =
            new NioEventLoopGroup(0, ConcurrencyUtil.newNamedThreadFactory("Python Console NIO Event Loop Boss")); // (1)

        // The second one, often called 'worker', handles the traffic of the
        // accepted connection once the boss accepts the connection and registers
        // the accepted connection to the worker.
        private final NioEventLoopGroup myWorkerGroup =
            new NioEventLoopGroup(0, ConcurrencyUtil.newNamedThreadFactory("Python Console NIO Event Loop Worker"));

        private final CountDownLatch myServerBound = new CountDownLatch(1);

        NettyServer(String host, int port) {
            myHost = host;
            myPort = port;
        }

        void listen() throws InterruptedException {
            // ServerBootstrap is a helper class that sets up a server. You can set
            // up the server using a Channel directly. However, please note that this
            // is a tedious process, and you do not need to do that in most cases.
            ServerBootstrap b = new ServerBootstrap(); // (2)
            b.group(myBossGroup, myWorkerGroup)
                // Here, we specify to use the NioServerSocketChannel class which is
                // used to instantiate a new Channel to accept incoming connections.
                .channel(NioServerSocketChannel.class) // (3)
                // The handler specified here will always be evaluated by a newly
                // accepted Channel. The ChannelInitializer is a special handler that
                // is purposed to help a user configure a new Channel. It is most
                // likely that you want to configure the ChannelPipeline of the new
                // Channel by adding some handlers such as DiscardServerHandler to
                // implement your network application. As the application gets
                // complicated, it is likely that you will add more handlers to the
                // pipeline and extract this anonymous class into a top level class
                // eventually.
                .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        LOG.debug("Connection to Thrift server on " + myPort + " received");

                        ch.pipeline().addLast(new LoggingHandler("#" + TNettyServerTransport.class.getName()));

                        // `FixedLengthFrameDecoder` is excessive but convenient
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                        ch.pipeline().addLast(new LengthFieldPrepender(4));
                        // `DirectedMessageCodec` is above Thrift messages
                        ch.pipeline().addLast(new DirectedMessageCodec());

                        // here comes Thrift
                        TNettyTransport thriftTransport = new TNettyTransport(ch);
                        TNettyClientTransport reverseTransport = new TNettyClientTransport(ch);

                        ch.pipeline().addLast(new DirectedMessageHandler(reverseTransport.getOutputStream(), thriftTransport.getOutputStream()));

                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                thriftTransport.close();
                                reverseTransport.close();

                                super.channelInactive(ctx);
                            }
                        });

                        // ready!
                        myAcceptQueue.put(thriftTransport);
                        myReverseTransportQueue.put(reverseTransport);
                    }
                })
                // You can also set the parameters which are specific to the Channel
                // implementation. We are writing a TCP/IP server, so we are allowed to
                // set the socket options such as tcpNoDelay and keepAlive. Please
                // refer to the apidocs of ChannelOption and the specific ChannelConfig
                // implementations to get an overview about the supported
                // ChannelOptions.
                .option(ChannelOption.SO_BACKLOG, 128)          // (5)
                // Did you notice option() and childOption()? option() is for the
                // NioServerSocketChannel that accepts incoming connections.
                // childOption() is for the Channels accepted by the parent
                // ServerChannel, which is NioServerSocketChannel in this case.
                .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

            // Bind and start to accept incoming connections.
            // We are ready to go now. What's left is to bind to the port and to
            // start the server.
            b.bind(myHost, myPort).sync(); // (7)

            LOG.debug("Running Netty server on " + myPort);

            myServerBound.countDown();
        }

        /**
         * @throws InterruptedException  if {@link CountDownLatch#await} is interrupted
         * @throws ServerClosedException if {@link NettyServer} gets closed
         */
        void waitForBind() throws InterruptedException {
            while (!myClosed.get()) {
                if (myServerBound.await(100L, TimeUnit.MILLISECONDS)) {
                    return;
                }
            }
            throw new ServerClosedException();
        }

        TTransport accept() throws TTransportException {
            try {
                while (true) {
                    TTransport acceptedTransport = myAcceptQueue.poll(100L, TimeUnit.MILLISECONDS);
                    if (myClosed.get()) {
                        throw new TTransportException("Netty server is closed");
                    }
                    if (acceptedTransport != null) {
                        return acceptedTransport;
                    }
                }
            }
            catch (InterruptedException e) {
                throw new TTransportException(e);
            }
        }

        /**
         * @throws InterruptedException  if {@link BlockingQueue#poll} is interrupted
         * @throws ServerClosedException if {@link NettyServer} gets closed
         */
        TTransport takeReverseTransport() throws InterruptedException {
            while (!myClosed.get()) {
                TTransport element = myReverseTransportQueue.poll(100L, TimeUnit.MILLISECONDS);
                if (element != null) {
                    return element;
                }
            }
            throw new ServerClosedException();
        }

        /**
         * Shutdown the server {@link NioEventLoopGroup}.
         * <p>
         * Does not wait for the server socket to be closed.
         */
        void close() {
            if (myClosed.compareAndSet(false, true)) {
                LOG.debug("Closing Netty server");

                myWorkerGroup.shutdownGracefully();
                myBossGroup.shutdownGracefully();
            }
        }

        boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return myWorkerGroup.awaitTermination(timeout, unit) && myBossGroup.awaitTermination(timeout, unit);
        }
    }

    private static class TNettyTransport extends TCumulativeTransport {
        private final SocketChannel myChannel;

        TNettyTransport(SocketChannel channel) {
            myChannel = channel;
        }

        @Override
        public boolean isOpen() {
            return myChannel.isOpen();
        }

        @Override
        public void writeMessage(byte[] content) {
            myChannel.writeAndFlush(new DirectedMessage(DirectedMessage.MessageDirection.RESPONSE, content));
        }

        /**
         * Workarounds the problem of the original method where the generic {@link TTransportException} is thrown even if the client side
         * has properly finished its work. The generic exceptions are not ignored in
         * {@code org.apache.thrift.server.TThreadPoolServer.WorkerProcess.run}, these exceptions are rethrown and they are shown in as the
         * error in the IDE.
         * <p>
         * The workaround is to throw {@link TTransportException} of the specific type {@link TTransportException#END_OF_FILE}, which is
         * ignored by the said {@code run} method.
         */
        @Override
        public int readAll(byte[] buf, int off, int len) throws TTransportException {
            int got = 0;
            while (got < len) {
                int ret = read(buf, off + got, len - got);
                if (ret <= 0) {
                    if (got == 0) {
                        // properly handle the case when the client seems to have finished its working session normally
                        throw new TTransportException(TTransportException.END_OF_FILE);
                    }
                    else {
                        throw new TTransportException(
                            "Cannot read. Remote side has closed. Tried to read "
                                + len
                                + " bytes, but only got "
                                + got
                                + " bytes. (This is often indicative of an internal error on the server side. Please check your server logs.)");
                    }
                }
                got += ret;
            }
            return got;
        }

        @Override
        public @Nullable TConfiguration getConfiguration() {
            return null;
        }

        @Override
        public void updateKnownMessageSize(long size) {
        }

        @Override
        public void checkReadBytesAvailable(long numBytes) {
        }
    }

    private static class TNettyClientTransport extends TCumulativeTransport {
        private final SocketChannel myChannel;

        TNettyClientTransport(SocketChannel channel) {
            myChannel = channel;
        }

        @Override
        public boolean isOpen() {
            return myChannel.isOpen();
        }

        @Override
        public @Nullable TConfiguration getConfiguration() {
            return null;
        }

        @Override
        public void updateKnownMessageSize(long size) {
        }

        @Override
        public void checkReadBytesAvailable(long numBytes) {
        }

        @Override
        public void writeMessage(byte[] content) {
            myChannel.writeAndFlush(new DirectedMessage(DirectedMessage.MessageDirection.REQUEST, content));
        }
    }
}
