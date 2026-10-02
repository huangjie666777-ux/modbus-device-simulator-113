package com.example.modbus.device;

import com.example.modbus.config.SimulatorConfig;

import java.util.HashMap;
import java.util.Map;

/** Maps unit IDs to their shared in-memory device state. */
public final class DeviceRegistry {

    private final Map<Integer, DeviceMemory> devices = new HashMap<>();

    public DeviceRegistry(SimulatorConfig config) {
        for (SimulatorConfig.Unit unit : config.units) {
            devices.put(unit.unitId(), new DeviceMemory(unit));
        }
    }

    /** Returns null for unknown unit IDs. */
    public DeviceMemory get(int unitId) {
        return devices.get(unitId);
    }
}
