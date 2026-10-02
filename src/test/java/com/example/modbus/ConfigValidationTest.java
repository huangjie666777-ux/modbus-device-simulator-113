package com.example.modbus;

import com.example.modbus.config.SimulatorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidationTest {

    @TempDir
    Path dir;

    private Path write(String json) throws Exception {
        Path p = dir.resolve("c.json");
        Files.writeString(p, json);
        return p;
    }

    @Test
    void validConfigLoads() throws Exception {
        SimulatorConfig c = SimulatorConfig.load(write(
                "{\"port\":15020,\"units\":[{\"unitId\":1,\"coils\":{\"start\":0,\"values\":[true]},"
                        + "\"holdingRegisters\":{\"start\":0,\"values\":[65535]}}]}"));
        assertEquals(15020, c.port);
        assertEquals(1, c.units.size());
    }

    @Test
    void duplicateUnitIdRejected() throws Exception {
        String unit = "{\"unitId\":1,\"coils\":{\"start\":0,\"values\":[true]}}";
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.load(
                write("{\"port\":15020,\"units\":[" + unit + "," + unit + "]}")));
    }

    @Test
    void outOfBoundsRangeRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.load(write(
                "{\"port\":15020,\"units\":[{\"unitId\":1,\"coils\":{\"start\":65535,\"values\":[true,true]}}]}")));
    }

    @Test
    void illegalInitialValuesRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.load(write(
                "{\"port\":15020,\"units\":[{\"unitId\":1,\"holdingRegisters\":{\"start\":0,\"values\":[70000]}}]}")));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.load(write(
                "{\"port\":15020,\"units\":[{\"unitId\":1,\"coils\":{\"start\":0,\"values\":[1]}}]}")));
    }

    @Test
    void lowPortRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.load(write(
                "{\"port\":502,\"units\":[{\"unitId\":1,\"coils\":{\"start\":0,\"values\":[true]}}]}")));
    }
}
