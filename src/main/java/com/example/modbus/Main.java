package com.example.modbus;

import com.example.modbus.config.SimulatorConfig;
import com.example.modbus.server.ModbusServer;

import java.nio.file.Path;

/** Entry point: java -jar ... [config.json] (default: config.json). */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(args.length > 0 ? args[0] : "config.json");
        SimulatorConfig config = SimulatorConfig.load(configPath);
        try (ModbusServer server = new ModbusServer(config)) {
            server.start();
            Thread.currentThread().join();
        }
    }
}
