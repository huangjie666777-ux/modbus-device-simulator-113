package com.example.modbus.protocol;

public final class ModbusExceptionCode {
    public static final int ILLEGAL_FUNCTION = 0x01;
    public static final int ILLEGAL_DATA_ADDRESS = 0x02;
    public static final int ILLEGAL_DATA_VALUE = 0x03;
    public static final int GATEWAY_TARGET_DEVICE_FAILED = 0x0B;

    private ModbusExceptionCode() {}
}
