package com.example.modbus.protocol;

import com.example.modbus.device.DeviceMemory;
import com.example.modbus.device.DeviceRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public final class ModbusRequestHandler extends SimpleChannelInboundHandler<ModbusRequest> {
    private static final int READ_COILS = 0x01;
    private static final int READ_HOLDING_REGISTERS = 0x03;
    private static final int WRITE_SINGLE_COIL = 0x05;
    private static final int WRITE_SINGLE_REGISTER = 0x06;
    private static final int WRITE_MULTIPLE_COILS = 0x0F;
    private static final int WRITE_MULTIPLE_REGISTERS = 0x10;

    private final DeviceRegistry registry;

    public ModbusRequestHandler(DeviceRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, ModbusRequest request) {
        context.writeAndFlush(handle(request));
    }

    private ModbusResponse handle(ModbusRequest request) {
        DeviceMemory device = registry.find(request.unitId());
        if (device == null) {
            return exception(request, ModbusExceptionCode.GATEWAY_TARGET_DEVICE_FAILED);
        }

        return switch (request.functionCode()) {
            case READ_COILS -> readCoils(request, device);
            case READ_HOLDING_REGISTERS -> readHoldingRegisters(request, device);
            case WRITE_SINGLE_COIL -> writeSingleCoil(request, device);
            case WRITE_SINGLE_REGISTER -> writeSingleRegister(request, device);
            case WRITE_MULTIPLE_COILS -> writeMultipleCoils(request, device);
            case WRITE_MULTIPLE_REGISTERS -> writeMultipleRegisters(request, device);
            default -> exception(request, ModbusExceptionCode.ILLEGAL_FUNCTION);
        };
    }

    private ModbusResponse readCoils(ModbusRequest request, DeviceMemory device) {
        byte[] pdu = request.pdu();
        if (pdu.length != 5) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        if (quantity < 1 || quantity > DeviceMemory.MAX_READ_BITS) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        boolean[] values = device.readCoils(address, quantity);
        if (values == null) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_ADDRESS);
        }

        int byteCount = (quantity + 7) / 8;
        byte[] response = new byte[2 + byteCount];
        response[0] = (byte) READ_COILS;
        response[1] = (byte) byteCount;
        for (int i = 0; i < quantity; i++) {
            if (values[i]) {
                response[2 + i / 8] |= (byte) (1 << (i % 8));
            }
        }
        return new ModbusResponse(request.transactionId(), request.unitId(), response);
    }

    private ModbusResponse readHoldingRegisters(ModbusRequest request, DeviceMemory device) {
        byte[] pdu = request.pdu();
        if (pdu.length != 5) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        if (quantity < 1 || quantity > DeviceMemory.MAX_READ_REGISTERS) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        char[] values = device.readRegisters(address, quantity);
        if (values == null) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_ADDRESS);
        }

        byte[] response = new byte[2 + quantity * 2];
        response[0] = (byte) READ_HOLDING_REGISTERS;
        response[1] = (byte) (quantity * 2);
        putRegisters(response, 2, values);
        return new ModbusResponse(request.transactionId(), request.unitId(), response);
    }

    private ModbusResponse writeSingleCoil(ModbusRequest request, DeviceMemory device) {
        byte[] pdu = request.pdu();
        if (pdu.length != 5) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        int address = u16(pdu, 1);
        int rawValue = u16(pdu, 3);
        if (rawValue != 0x0000 && rawValue != 0xFF00) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        if (!device.writeCoil(address, rawValue == 0xFF00)) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_ADDRESS);
        }
        return echo(request);
    }

    private ModbusResponse writeSingleRegister(ModbusRequest request, DeviceMemory device) {
        byte[] pdu = request.pdu();
        if (pdu.length != 5) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        int address = u16(pdu, 1);
        int value = u16(pdu, 3);
        if (!device.writeRegister(address, value)) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_ADDRESS);
        }
        return echo(request);
    }

    private ModbusResponse writeMultipleCoils(ModbusRequest request, DeviceMemory device) {
        byte[] pdu = request.pdu();
        if (pdu.length < 6) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        int byteCount = pdu[5] & 0xFF;
        if (quantity < 1 || quantity > DeviceMemory.MAX_WRITE_BITS
                || byteCount != (quantity + 7) / 8 || pdu.length != 6 + byteCount) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }

        boolean[] values = new boolean[quantity];
        for (int i = 0; i < quantity; i++) {
            values[i] = ((pdu[6 + i / 8] >> (i % 8)) & 1) == 1;
        }
        if (!device.writeCoils(address, values)) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_ADDRESS);
        }

        byte[] response = new byte[5];
        response[0] = (byte) WRITE_MULTIPLE_COILS;
        putU16(response, 1, address);
        putU16(response, 3, quantity);
        return new ModbusResponse(request.transactionId(), request.unitId(), response);
    }

    private ModbusResponse writeMultipleRegisters(ModbusRequest request, DeviceMemory device) {
        byte[] pdu = request.pdu();
        if (pdu.length < 6) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        int byteCount = pdu[5] & 0xFF;
        if (quantity < 1 || quantity > DeviceMemory.MAX_WRITE_REGISTERS
                || byteCount != quantity * 2 || pdu.length != 6 + byteCount) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_VALUE);
        }

        char[] values = new char[quantity];
        for (int i = 0; i < quantity; i++) {
            values[i] = (char) u16(pdu, 6 + i * 2);
        }
        if (!device.writeRegisters(address, values)) {
            return exception(request, ModbusExceptionCode.ILLEGAL_DATA_ADDRESS);
        }

        byte[] response = new byte[5];
        response[0] = (byte) WRITE_MULTIPLE_REGISTERS;
        putU16(response, 1, address);
        putU16(response, 3, quantity);
        return new ModbusResponse(request.transactionId(), request.unitId(), response);
    }

    private ModbusResponse echo(ModbusRequest request) {
        return new ModbusResponse(request.transactionId(), request.unitId(), request.pdu().clone());
    }

    private ModbusResponse exception(ModbusRequest request, int code) {
        return new ModbusResponse(request.transactionId(), request.unitId(),
                new byte[]{(byte) (request.functionCode() | 0x80), (byte) code});
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static void putU16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void putRegisters(byte[] bytes, int offset, char[] values) {
        for (int i = 0; i < values.length; i++) {
            putU16(bytes, offset + i * 2, values[i]);
        }
    }
}
