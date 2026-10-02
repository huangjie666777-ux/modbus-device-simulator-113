package com.example.modbus;

import com.example.modbus.codec.ModbusRequest;
import com.example.modbus.codec.ModbusResponse;
import com.example.modbus.config.SimulatorConfig;
import com.example.modbus.device.DeviceRegistry;
import com.example.modbus.server.ModbusRequestHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FunctionCodeTest {

    @TempDir
    Path dir;

    private ModbusRequestHandler handler;
    private DeviceRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        Path p = dir.resolve("c.json");
        Files.writeString(p, "{\"port\":15020,\"units\":["
                + "{\"unitId\":1,\"coils\":{\"start\":0,\"values\":[true,false,true,false,true,false,true,false]},"
                + "\"holdingRegisters\":{\"start\":0,\"values\":[100,200,300,400]}},"
                + "{\"unitId\":2,\"coils\":{\"start\":0,\"values\":[false,false,false,false]},"
                + "\"holdingRegisters\":{\"start\":0,\"values\":[1,2]}}]}");
        SimulatorConfig config = SimulatorConfig.load(p);
        registry = new DeviceRegistry(config);
        handler = newHandler(registry);
    }

    private static ModbusRequestHandler newHandler(DeviceRegistry r) throws Exception {
        return new ModbusRequestHandler(r);
    }

    private static ModbusResponse invoke(ModbusRequestHandler h, ModbusRequest req) throws Exception {
        Method m = ModbusRequestHandler.class.getDeclaredMethod("handle", ModbusRequest.class);
        m.setAccessible(true);
        return (ModbusResponse) m.invoke(h, req);
    }

    private static ModbusRequest req(int unit, int... pduInts) {
        byte[] pdu = new byte[pduInts.length];
        for (int i = 0; i < pdu.length; i++) pdu[i] = (byte) pduInts[i];
        return new ModbusRequest(7, unit, pdu[0] & 0xFF, pdu);
    }

    @Test
    void fc1PacksCoilsLsbFirst() throws Exception {
        ModbusResponse r = invoke(handler, req(1, (byte) 0x01, 0, 0, 0, 8));
        assertEquals(1, r.functionCode());
        assertArrayEquals(new byte[]{1, 0x55}, r.data());
    }

    @Test
    void fc1UnusedBitsAreZero() throws Exception {
        ModbusResponse r = invoke(handler, req(1, (byte) 0x01, 0, 0, 0, 3));
        assertArrayEquals(new byte[]{1, 0x05}, r.data());
    }

    @Test
    void fc3ReturnsBigEndianRegisters() throws Exception {
        ModbusResponse r = invoke(handler, req(1, (byte) 0x03, 0, 1, 0, 2));
        assertArrayEquals(new byte[]{4, 0, (byte) 200, 1, 0x2C}, r.data());
    }

    @Test
    void fc5AcceptsOnlyFF00Or0000() throws Exception {
        ModbusResponse ok = invoke(handler, req(1, (byte) 0x05, 0, 1, (byte) 0xFF, 0));
        assertEquals(5, ok.functionCode());
        assertTrue(registry.get(1).readCoils(1, 1)[0]);
        ModbusResponse bad = invoke(handler, req(1, (byte) 0x05, 0, 1, 0x12, 0x34));
        assertEquals(0x85, bad.functionCode());
        assertEquals(3, bad.data()[0]);
    }

    @Test
    void fc6WritesSingleRegister() throws Exception {
        invoke(handler, req(1, (byte) 0x06, 0, 0, 0x12, 0x34));
        assertEquals(0x1234, registry.get(1).readRegisters(0, 1)[0]);
    }

    @Test
    void fc15WritesMultipleCoilsAtomically() throws Exception {
        ModbusResponse r = invoke(handler, req(1, 0x0F, 0, 0, 0, 8, 1, 0xAA));
        assertEquals(15, r.functionCode());
        assertArrayEquals(new boolean[]{false, true, false, true, false, true, false, true},
                registry.get(1).readCoils(0, 8));
    }

    @Test
    void fc15FailureChangesNothing() throws Exception {
        boolean[] before = registry.get(1).readCoils(0, 8);
        ModbusResponse r = invoke(handler, req(1, 0x0F, 0, 6, 0, 8, 1, 0xFF)); // overruns range
        assertEquals(0x8F, r.functionCode());
        assertEquals(2, r.data()[0]);
        assertArrayEquals(before, registry.get(1).readCoils(0, 8));
    }

    @Test
    void fc16WritesMultipleRegistersAtomically() throws Exception {
        ModbusResponse r = invoke(handler, req(1, (byte) 0x10, 0, 1, 0, 2, 4, 0, 9, 0, 8));
        assertEquals(16, r.functionCode());
        assertArrayEquals(new int[]{100, 9, 8, 400}, registry.get(1).readRegisters(0, 4));
    }

    @Test
    void fc16FailureChangesNothing() throws Exception {
        int[] before = registry.get(1).readRegisters(0, 4);
        ModbusResponse r = invoke(handler, req(1, (byte) 0x10, 0, 3, 0, 2, 4, 0, 9, 0, 8)); // overruns
        assertEquals(0x90, r.functionCode());
        assertEquals(2, r.data()[0]);
        assertArrayEquals(before, registry.get(1).readRegisters(0, 4));
    }

    @Test
    void unknownFunctionReturns01() throws Exception {
        ModbusResponse r = invoke(handler, req(1, (byte) 0x2B, 1, 1));
        assertEquals(0xAB, r.functionCode());
        assertEquals(1, r.data()[0]);
    }

    @Test
    void unconfiguredAddressReturns02() throws Exception {
        ModbusResponse r = invoke(handler, req(1, (byte) 0x03, 0, 100, 0, 1));
        assertEquals(0x83, r.functionCode());
        assertEquals(2, r.data()[0]);
    }

    @Test
    void illegalQuantityReturns03() throws Exception {
        ModbusResponse r = invoke(handler, req(1, (byte) 0x01, 0, 0, 0, 0));
        assertEquals(0x81, r.functionCode());
        assertEquals(3, r.data()[0]);
        ModbusResponse r2 = invoke(handler, req(1, (byte) 0x03, 0, 0, 0, 126));
        assertEquals(3, r2.data()[0]);
    }

    @Test
    void unknownUnitReturns0B() throws Exception {
        ModbusResponse r = invoke(handler, req(9, (byte) 0x03, 0, 0, 0, 1));
        assertEquals(0x83, r.functionCode());
        assertEquals(0x0B, r.data()[0]);
    }

    @Test
    void unitsAreIndependent() throws Exception {
        invoke(handler, req(1, (byte) 0x06, 0, 0, 0, 42));
        assertEquals(1, registry.get(2).readRegisters(0, 1)[0]);
        assertEquals(42, registry.get(1).readRegisters(0, 1)[0]);
    }

    @Test
    void responsePreservesTransactionAndUnit() throws Exception {
        ModbusResponse r = invoke(handler, req(2, (byte) 0x03, 0, 0, 0, 1));
        assertEquals(7, r.transactionId());
        assertEquals(2, r.unitId());
    }
}
