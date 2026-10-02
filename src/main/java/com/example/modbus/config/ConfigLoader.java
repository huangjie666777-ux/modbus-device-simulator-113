package com.example.modbus.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConfigLoader {
    private static final int ADDRESS_SPACE = 65536;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SimulatorConfig load(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            SimulatorConfig config = objectMapper.readValue(input, SimulatorConfig.class);
            validate(config);
            return config;
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取配置文件: " + path + " (" + e.getMessage() + ")", e);
        }
    }

    private void validate(SimulatorConfig config) {
        ServerConfig server = config.getServer();
        if (server == null) {
            throw new IllegalArgumentException("缺少 server 配置");
        }
        if (server.getHost() == null || server.getHost().isBlank()) {
            throw new IllegalArgumentException("server.host 不能为空");
        }
        if (server.getPort() < 1024 || server.getPort() > 65535) {
            throw new IllegalArgumentException("server.port 必须位于 1024..65535");
        }
        if (server.getMaxConnections() < 1 || server.getMaxConnections() > 10000) {
            throw new IllegalArgumentException("server.maxConnections 必须位于 1..10000");
        }
        if (server.getMaxFrameLength() < 9 || server.getMaxFrameLength() > 4096) {
            throw new IllegalArgumentException("server.maxFrameLength 必须位于 9..4096");
        }

        List<DeviceConfig> devices = config.getDevices();
        if (devices == null || devices.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个 device");
        }

        Set<Integer> unitIds = new HashSet<>();
        for (DeviceConfig device : devices) {
            validateDevice(device, unitIds);
        }
    }

    private void validateDevice(DeviceConfig device, Set<Integer> unitIds) {
        int unitId = device.getUnitId();
        if (unitId < 0 || unitId > 247 || !unitIds.add(unitId)) {
            throw new IllegalArgumentException("UnitID 非法或重复: " + unitId);
        }
        validateRegion("coil", device.getCoilStartAddress(), device.getCoils() == null ? 0 : device.getCoils().size());
        validateRegion("holding register", device.getHoldingStartAddress(),
                device.getHoldingRegisters() == null ? 0 : device.getHoldingRegisters().size());

        if (device.getCoils() != null) {
            for (Boolean value : device.getCoils()) {
                if (value == null) {
                    throw new IllegalArgumentException("Unit " + unitId + " 的线圈初值不能为 null");
                }
            }
        }
        if (device.getHoldingRegisters() != null) {
            for (Integer value : device.getHoldingRegisters()) {
                if (value == null || value < 0 || value > 65535) {
                    throw new IllegalArgumentException("Unit " + unitId + " 的保持寄存器初值必须位于 0..65535");
                }
            }
        }
    }

    private void validateRegion(String name, int start, int length) {
        if (start < 0 || start >= ADDRESS_SPACE) {
            throw new IllegalArgumentException(name + " 起始地址必须位于 0..65535");
        }
        if (length <= 0 || (long) start + length > ADDRESS_SPACE) {
            throw new IllegalArgumentException(name + " 区间越界: start=" + start + ", length=" + length);
        }
    }
}
