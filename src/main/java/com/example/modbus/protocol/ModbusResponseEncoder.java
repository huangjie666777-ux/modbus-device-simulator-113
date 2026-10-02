package com.example.modbus.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public final class ModbusResponseEncoder extends MessageToMessageEncoder<ModbusResponse> {
    @Override
    protected void encode(ChannelHandlerContext context, ModbusResponse response, List<Object> output) {
        ByteBuf buffer = context.alloc().buffer(6 + response.pdu().length);
        buffer.writeShort(response.transactionId());
        buffer.writeShort(0);
        buffer.writeShort(1 + response.pdu().length);
        buffer.writeByte(response.unitId());
        buffer.writeBytes(response.pdu());
        output.add(buffer);
    }
}
