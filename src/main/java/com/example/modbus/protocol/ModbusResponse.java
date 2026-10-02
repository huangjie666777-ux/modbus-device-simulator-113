package com.example.modbus.protocol;

public record ModbusResponse(int transactionId, int unitId, byte[] pdu) {
}
