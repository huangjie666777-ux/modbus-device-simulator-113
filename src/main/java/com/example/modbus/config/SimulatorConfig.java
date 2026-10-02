package com.example.modbus.config;

import java.util.List;

public final class SimulatorConfig {
    private ServerConfig server = new ServerConfig();
    private List<DeviceConfig> devices = List.of();

    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig server) { this.server = server; }

    public List<DeviceConfig> getDevices() { return devices; }
    public void setDevices(List<DeviceConfig> devices) { this.devices = devices; }
}
