package com.example.modbus.server;

import com.example.modbus.codec.ModbusRequest;
import com.example.modbus.codec.ModbusResponse;
import com.example.modbus.device.DeviceMemory;
import com.example.modbus.device.DeviceRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Implements Modbus V1.1b3 function codes 1, 3, 5, 6, 15, 16 against
 * the shared in-memory device state. A failed request never affects
 * subsequent requests on the same connection.
 */
public final class ModbusRequestHandler extends SimpleChannelInboundHandler<ModbusRequest> {

    // Exception codes per Modbus V1.1b3.
    static final int EX_ILLEGAL_FUNCTION = 0x01;
    static final int EX_ILLEGAL_DATA_ADDRESS = 0x02;
    static final int EX_ILLEGAL_DATA_VALUE = 0x03;
    static final int EX_GATEWAY_TARGET_NO_RESPONSE = 0x0B;

    private final DeviceRegistry registry;

    public ModbusRequestHandler(DeviceRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ModbusRequest req) {
        ctx.writeAndFlush(handle(req));
    }

    ModbusResponse handle(ModbusRequest req) {
        DeviceMemory device = registry.get(req.unitId());
        if (device == null) {
            return ModbusResponse.exception(req, EX_GATEWAY_TARGET_NO_RESPONSE);
        }
        try {
            return switch (req.functionCode()) {
                case 1 -> readCoils(req, device);
                case 3 -> readHoldingRegisters(req, device);
                case 5 -> writeSingleCoil(req, device);
                case 6 -> writeSingleRegister(req, device);
                case 15 -> writeMultipleCoils(req, device);
                case 16 -> writeMultipleRegisters(req, device);
                default -> ModbusResponse.exception(req, EX_ILLEGAL_FUNCTION);
            };
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        }
    }

    private static int u16(byte[] pdu, int offset) {
        return ((pdu[offset] & 0xFF) << 8) | (pdu[offset + 1] & 0xFF);
    }

    private ModbusResponse readCoils(ModbusRequest req, DeviceMemory device) {
        byte[] pdu = req.pdu();
        if (pdu.length != 5) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        if (quantity < 1 || quantity > 2000) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        if (!device.hasCoilRange(address, quantity)) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_ADDRESS);
        boolean[] values = device.readCoils(address, quantity);
        int byteCount = (quantity + 7) / 8;
        byte[] data = new byte[1 + byteCount];
        data[0] = (byte) byteCount;
        for (int i = 0; i < quantity; i++) {
            if (values[i]) data[1 + i / 8] |= (byte) (1 << (i % 8)); // LSB first
        }
        return ModbusResponse.normal(req, data);
    }

    private ModbusResponse readHoldingRegisters(ModbusRequest req, DeviceMemory device) {
        byte[] pdu = req.pdu();
        if (pdu.length != 5) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        if (quantity < 1 || quantity > 125) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        if (!device.hasRegisterRange(address, quantity)) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_ADDRESS);
        int[] values = device.readRegisters(address, quantity);
        byte[] data = new byte[1 + quantity * 2];
        data[0] = (byte) (quantity * 2);
        for (int i = 0; i < quantity; i++) {
            data[1 + i * 2] = (byte) (values[i] >> 8); // big-endian
            data[2 + i * 2] = (byte) values[i];
        }
        return ModbusResponse.normal(req, data);
    }

    private ModbusResponse writeSingleCoil(ModbusRequest req, DeviceMemory device) {
        byte[] pdu = req.pdu();
        if (pdu.length != 5) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        int address = u16(pdu, 1);
        int value = u16(pdu, 3);
        if (value != 0xFF00 && value != 0x0000) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        if (!device.hasCoilRange(address, 1)) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_ADDRESS);
        device.writeCoil(address, value == 0xFF00);
        return ModbusResponse.normal(req, new byte[]{pdu[1], pdu[2], pdu[3], pdu[4]});
    }

    private ModbusResponse writeSingleRegister(ModbusRequest req, DeviceMemory device) {
        byte[] pdu = req.pdu();
        if (pdu.length != 5) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        int address = u16(pdu, 1);
        int value = u16(pdu, 3);
        if (!device.hasRegisterRange(address, 1)) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_ADDRESS);
        device.writeRegister(address, value);
        return ModbusResponse.normal(req, new byte[]{pdu[1], pdu[2], pdu[3], pdu[4]});
    }

    private ModbusResponse writeMultipleCoils(ModbusRequest req, DeviceMemory device) {
        byte[] pdu = req.pdu();
        if (pdu.length < 6) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        int byteCount = pdu[5] & 0xFF;
        if (quantity < 1 || quantity > 1968 || byteCount != (quantity + 7) / 8 || pdu.length != 6 + byteCount) {
            return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        }
        if (!device.hasCoilRange(address, quantity)) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_ADDRESS);
        boolean[] values = new boolean[quantity];
        for (int i = 0; i < quantity; i++) {
            values[i] = (pdu[6 + i / 8] & (1 << (i % 8))) != 0;
        }
        device.writeCoils(address, values); // validated above: atomic commit
        return ModbusResponse.normal(req, new byte[]{pdu[1], pdu[2], pdu[3], pdu[4]});
    }

    private ModbusResponse writeMultipleRegisters(ModbusRequest req, DeviceMemory device) {
        byte[] pdu = req.pdu();
        if (pdu.length < 6) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        int address = u16(pdu, 1);
        int quantity = u16(pdu, 3);
        int byteCount = pdu[5] & 0xFF;
        if (quantity < 1 || quantity > 123 || byteCount != quantity * 2 || pdu.length != 6 + byteCount) {
            return ModbusResponse.exception(req, EX_ILLEGAL_DATA_VALUE);
        }
        if (!device.hasRegisterRange(address, quantity)) return ModbusResponse.exception(req, EX_ILLEGAL_DATA_ADDRESS);
        int[] values = new int[quantity];
        for (int i = 0; i < quantity; i++) {
            values[i] = u16(pdu, 6 + i * 2);
        }
        device.writeRegisters(address, values); // validated above: atomic commit
        return ModbusResponse.normal(req, new byte[]{pdu[1], pdu[2], pdu[3], pdu[4]});
    }
}
