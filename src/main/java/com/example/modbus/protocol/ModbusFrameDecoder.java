package com.example.modbus.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public final class ModbusFrameDecoder extends ByteToMessageDecoder {
    private static final int HEADER_LENGTH = 6;
    private static final int VALID_PROTOCOL_ID = 0;

    private final int maxFrameLength;

    public ModbusFrameDecoder(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
        setSingleDecode(false);
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
        while (input.readableBytes() >= HEADER_LENGTH) {
            input.markReaderIndex();
            int transactionId = input.readUnsignedShort();
            int protocolId = input.readUnsignedShort();
            int length = input.readUnsignedShort();

            if (protocolId != VALID_PROTOCOL_ID || length < 2 || HEADER_LENGTH + length > maxFrameLength) {
                context.close();
                return;
            }
            if (input.readableBytes() < length) {
                input.resetReaderIndex();
                return;
            }

            int unitId = input.readUnsignedByte();
            byte[] pdu = new byte[length - 1];
            input.readBytes(pdu);
            if (pdu.length == 0) {
                context.close();
                return;
            }
            output.add(new ModbusRequest(transactionId, unitId, pdu[0] & 0xFF, pdu));
        }
    }
}
