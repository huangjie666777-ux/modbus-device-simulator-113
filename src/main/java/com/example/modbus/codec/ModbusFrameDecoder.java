package com.example.modbus.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Splits the TCP stream into Modbus TCP frames using the MBAP length field.
 * Handles half packets and sticky packets. Frames with an illegal protocol
 * identifier or length close the connection without allocating large buffers.
 */
public final class ModbusFrameDecoder extends ByteToMessageDecoder {

    private static final int MBAP_HEADER = 7;
    /** unitId (1) + function code (1) + max PDU payload (252). */
    private static final int MAX_MBAP_LENGTH = 254;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.readableBytes() >= MBAP_HEADER) {
            in.markReaderIndex();
            int transactionId = in.readUnsignedShort();
            int protocolId = in.readUnsignedShort();
            int length = in.readUnsignedShort();
            int unitId = in.readUnsignedByte();

            if (protocolId != 0 || length < 2 || length > MAX_MBAP_LENGTH) {
                ctx.close(); // illegal frame: drop the connection
                return;
            }
            if (in.readableBytes() < length - 1) {
                in.resetReaderIndex(); // half packet: wait for more bytes
                return;
            }
            byte[] pdu = new byte[length - 1];
            in.readBytes(pdu);
            out.add(new ModbusRequest(transactionId, unitId, pdu[0] & 0xFF, pdu));
        }
    }
}
