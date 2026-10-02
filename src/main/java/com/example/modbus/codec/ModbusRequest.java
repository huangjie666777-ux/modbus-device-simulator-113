package com.example.modbus.codec;

/** A fully framed Modbus TCP request. The PDU includes the function code at index 0. */
public record ModbusRequest(int transactionId, int unitId, int functionCode, byte[] pdu) {
}
