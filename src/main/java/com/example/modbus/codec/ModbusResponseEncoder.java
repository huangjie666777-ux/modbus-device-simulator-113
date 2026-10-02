package com.example.modbus.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/** Encodes responses as MBAP + PDU, preserving transaction and unit IDs. */
public final class ModbusResponseEncoder extends MessageToByteEncoder<ModbusResponse> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ModbusResponse resp, ByteBuf out) {
        int length = 1 + 1 + resp.data().length; // unitId + functionCode + data
        out.writeShort(resp.transactionId());
        out.writeShort(0); // protocol identifier
        out.writeShort(length);
        out.writeByte(resp.unitId());
        out.writeByte(resp.functionCode());
        out.writeBytes(resp.data());
    }
}
