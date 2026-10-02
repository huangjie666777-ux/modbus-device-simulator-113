package com.example.modbus.device;

import com.example.modbus.config.SimulatorConfig;

import java.util.HashMap;
import java.util.Map;

public final class DeviceRegistry {
    private final Map<Integer, DeviceMemory> devices = new HashMap<>();

    public DeviceRegistry(SimulatorConfig config) {
        config.getDevices().forEach(device -> devices.put(device.getUnitId(), new DeviceMemory(device)));
    }

    public DeviceMemory find(int unitId) {
        return devices.get(unitId);
    }
}
