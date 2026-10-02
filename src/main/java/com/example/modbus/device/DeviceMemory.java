package com.example.modbus.device;

import com.example.modbus.config.DeviceConfig;

import java.util.Arrays;

public final class DeviceMemory {
    public static final int MAX_READ_BITS = 2000;
    public static final int MAX_WRITE_BITS = 1968;
    public static final int MAX_READ_REGISTERS = 125;
    public static final int MAX_WRITE_REGISTERS = 123;

    private final int unitId;
    private final int coilStart;
    private final int registerStart;
    private volatile boolean[] coils;
    private volatile char[] registers;

    public DeviceMemory(DeviceConfig config) {
        this.unitId = config.getUnitId();
        this.coilStart = config.getCoilStartAddress();
        this.registerStart = config.getHoldingStartAddress();
        this.coils = new boolean[config.getCoils().size()];
        for (int i = 0; i < coils.length; i++) {
            coils[i] = Boolean.TRUE.equals(config.getCoils().get(i));
        }
        this.registers = new char[config.getHoldingRegisters().size()];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = (char) config.getHoldingRegisters().get(i).intValue();
        }
    }

    public int unitId() { return unitId; }

    public boolean[] readCoils(int address, int quantity) {
        int index = coilIndex(address);
        if (index < 0 || quantity < 1 || quantity > MAX_READ_BITS || index + quantity > coils.length) {
            return null;
        }
        synchronized (this) {
            return Arrays.copyOfRange(coils, index, index + quantity);
        }
    }

    public char[] readRegisters(int address, int quantity) {
        int index = registerIndex(address);
        if (index < 0 || quantity < 1 || quantity > MAX_READ_REGISTERS || index + quantity > registers.length) {
            return null;
        }
        synchronized (this) {
            return Arrays.copyOfRange(registers, index, index + quantity);
        }
    }

    public boolean writeCoil(int address, boolean value) {
        int index = coilIndex(address);
        if (index < 0 || index >= coils.length) {
            return false;
        }
        synchronized (this) {
            coils[index] = value;
        }
        return true;
    }

    public boolean writeRegister(int address, int value) {
        int index = registerIndex(address);
        if (index < 0 || index >= registers.length) {
            return false;
        }
        synchronized (this) {
            registers[index] = (char) value;
        }
        return true;
    }

    public boolean writeCoils(int address, boolean[] values) {
        int index = coilIndex(address);
        if (index < 0 || values.length == 0 || values.length > MAX_WRITE_BITS) {
            return false;
        }
        synchronized (this) {
            if (index + values.length > coils.length) {
                return false;
            }
            System.arraycopy(values, 0, coils, index, values.length);
            return true;
        }
    }

    public boolean writeRegisters(int address, char[] values) {
        int index = registerIndex(address);
        if (index < 0 || values.length == 0 || values.length > MAX_WRITE_REGISTERS) {
            return false;
        }
        synchronized (this) {
            if (index + values.length > registers.length) {
                return false;
            }
            System.arraycopy(values, 0, registers, index, values.length);
            return true;
        }
    }

    private int coilIndex(int address) {
        if (address < coilStart || address < 0) {
            return -1;
        }
        return address - coilStart;
    }

    private int registerIndex(int address) {
        if (address < registerStart || address < 0) {
            return -1;
        }
        return address - registerStart;
    }
}
