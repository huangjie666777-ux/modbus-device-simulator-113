package com.example.modbus.device;

import com.example.modbus.config.SimulatorConfig;

/**
 * In-memory coil and holding-register storage for one unit.
 * All operations are synchronized so a batch write is never
 * observed partially by concurrent readers.
 */
public final class DeviceMemory {

    private final int coilStart;
    private final boolean[] coils;
    private final int regStart;
    private final int[] registers;

    public DeviceMemory(SimulatorConfig.Unit unit) {
        this.coilStart = unit.coils().start();
        this.coils = unit.coils().coils() != null ? unit.coils().coils().clone() : new boolean[0];
        this.regStart = unit.holdingRegisters().start();
        this.registers = unit.holdingRegisters().registers() != null
                ? unit.holdingRegisters().registers().clone() : new int[0];
    }

    public synchronized boolean hasCoilRange(int address, int count) {
        return count > 0 && address >= coilStart && address + count <= coilStart + coils.length;
    }

    public synchronized boolean hasRegisterRange(int address, int count) {
        return count > 0 && address >= regStart && address + count <= regStart + registers.length;
    }

    public synchronized boolean[] readCoils(int address, int count) {
        boolean[] out = new boolean[count];
        System.arraycopy(coils, address - coilStart, out, 0, count);
        return out;
    }

    public synchronized int[] readRegisters(int address, int count) {
        int[] out = new int[count];
        System.arraycopy(registers, address - regStart, out, 0, count);
        return out;
    }

    public synchronized void writeCoil(int address, boolean value) {
        coils[address - coilStart] = value;
    }

    public synchronized void writeRegister(int address, int value) {
        registers[address - regStart] = value;
    }

    /** Atomic batch commit; caller must validate all addresses first. */
    public synchronized void writeCoils(int address, boolean[] values) {
        System.arraycopy(values, 0, coils, address - coilStart, values.length);
    }

    public synchronized void writeRegisters(int address, int[] values) {
        System.arraycopy(values, 0, registers, address - regStart, values.length);
    }
}
