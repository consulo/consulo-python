package com.jetbrains.python.console.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

public class DirectedMessageCodec extends ByteToMessageCodec<DirectedMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, DirectedMessage msg, ByteBuf out) {
        out.writeByte(msg.getDirection().ordinal());
        out.writeBytes(msg.getContent());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        byte directionByte = in.readByte();
        DirectedMessage.MessageDirection direction = DirectedMessage.MessageDirection.values()[directionByte];
        int length = in.readableBytes();
        byte[] content = new byte[length];
        in.readBytes(content);
        out.add(new DirectedMessage(direction, content));

        // `in` will be released later in `ByteToMessageDecoder.channelRead()`
    }
}
