package com.jetbrains.python.console.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

public class DirectedMessageHandler extends SimpleChannelInboundHandler<DirectedMessage> {
    private final OutputStream myResponseStream;
    private final OutputStream myRequestStream;

    private @Nullable Channel myChannel = null;

    public DirectedMessageHandler(OutputStream responseStream, OutputStream requestStream) {
        myResponseStream = responseStream;
        myRequestStream = requestStream;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        myChannel = ctx.channel();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DirectedMessage msg) throws IOException {
        OutputStream it = switch (msg.getDirection()) {
            case RESPONSE -> myResponseStream;
            case REQUEST -> myRequestStream;
        };
        it.write(msg.getContent());
        it.flush();
    }
}
