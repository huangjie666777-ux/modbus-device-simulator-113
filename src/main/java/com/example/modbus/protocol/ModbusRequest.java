package com.example.modbus.protocol;

public record ModbusRequest(int transactionId, int unitId, int functionCode, byte[] pdu) {
}
