package com.example.modbus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsDuplicateUnitIds() throws IOException {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load("""
                {"server":{"port":11502},"devices":[
                {"unitId":1,"coilStartAddress":0,"coils":[true],"holdingStartAddress":0,"holdingRegisters":[1]},
                {"unitId":1,"coilStartAddress":0,"coils":[false],"holdingStartAddress":0,"holdingRegisters":[2]}
                ]}"""));
        assertEquals(true, error.getMessage().contains("重复"));
    }

    @Test
    void rejectsOutOfRangeRegion() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> load("""
                {"server":{"port":11502},"devices":[
                {"unitId":1,"coilStartAddress":65535,"coils":[true,false],"holdingStartAddress":0,"holdingRegisters":[1]}
                ]}"""));
    }

    @Test
    void rejectsIllegalInitialRegisterValue() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> load("""
                {"server":{"port":11502},"devices":[
                {"unitId":1,"coilStartAddress":0,"coils":[],"holdingStartAddress":0,"holdingRegisters":[65536]}
                ]}"""));
    }

    private IllegalArgumentException load(String json) throws IOException {
        Path file = tempDir.resolve("config.json");
        Files.writeString(file, json);
        new ConfigLoader().load(file);
        return null;
    }
}
