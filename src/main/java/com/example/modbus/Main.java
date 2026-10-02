package com.example.modbus;

import com.example.modbus.config.ConfigLoader;
import com.example.modbus.config.SimulatorConfig;
import com.example.modbus.server.ModbusTcpServer;

import java.nio.file.Path;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(args.length > 0 ? args[0] : "config/modbus-simulator.json");
        SimulatorConfig config = new ConfigLoader().load(configPath);
        ModbusTcpServer server = new ModbusTcpServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        server.start();
        System.out.println("Modbus TCP simulator listening on "
                + config.getServer().getHost() + ":" + server.boundAddress().getPort());
        Thread.currentThread().join();
    }
}
