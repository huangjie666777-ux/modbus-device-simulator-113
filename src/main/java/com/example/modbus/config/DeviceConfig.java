package com.example.modbus.config;

import java.util.List;

public final class DeviceConfig {
    private int unitId;
    private int coilStartAddress;
    private List<Boolean> coils = List.of();
    private int holdingStartAddress;
    private List<Integer> holdingRegisters = List.of();

    public int getUnitId() { return unitId; }
    public void setUnitId(int unitId) { this.unitId = unitId; }

    public int getCoilStartAddress() { return coilStartAddress; }
    public void setCoilStartAddress(int coilStartAddress) { this.coilStartAddress = coilStartAddress; }

    public List<Boolean> getCoils() { return coils; }
    public void setCoils(List<Boolean> coils) { this.coils = coils; }

    public int getHoldingStartAddress() { return holdingStartAddress; }
    public void setHoldingStartAddress(int holdingStartAddress) { this.holdingStartAddress = holdingStartAddress; }

    public List<Integer> getHoldingRegisters() { return holdingRegisters; }
    public void setHoldingRegisters(List<Integer> holdingRegisters) { this.holdingRegisters = holdingRegisters; }
}
