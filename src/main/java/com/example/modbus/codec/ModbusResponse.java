package com.example.modbus.codec;

/** A Modbus TCP response (normal or exception). */
public record ModbusResponse(int transactionId, int unitId, int functionCode, byte[] data) {

    public static ModbusResponse normal(ModbusRequest req, byte[] data) {
        return new ModbusResponse(req.transactionId(), req.unitId(), req.functionCode(), data);
    }

    public static ModbusResponse exception(ModbusRequest req, int exceptionCode) {
        return new ModbusResponse(req.transactionId(), req.unitId(),
                req.functionCode() | 0x80, new byte[]{(byte) exceptionCode});
    }
}
