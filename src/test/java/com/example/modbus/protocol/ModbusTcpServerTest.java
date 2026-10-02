package com.example.modbus.protocol;

import com.example.modbus.config.DeviceConfig;
import com.example.modbus.config.ServerConfig;
import com.example.modbus.config.SimulatorConfig;
import com.example.modbus.server.ModbusTcpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModbusTcpServerTest {
    private ModbusTcpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        SimulatorConfig config = new SimulatorConfig();
        ServerConfig serverConfig = config.getServer();
        serverConfig.setHost("127.0.0.1");
        serverConfig.setPort(0);
        serverConfig.setMaxConnections(4);

        DeviceConfig unit1 = new DeviceConfig();
        unit1.setUnitId(1);
        unit1.setCoilStartAddress(0);
        unit1.setCoils(List.of(true, false, true, false, false, true, true, false, false, false, true, false));
        unit1.setHoldingStartAddress(0);
        unit1.setHoldingRegisters(List.of(100, 200, 300, 400, 500, 600, 700, 800, 0, 0, 0, 0, 0, 0, 0, 0));

        DeviceConfig unit2 = new DeviceConfig();
        unit2.setUnitId(2);
        unit2.setCoilStartAddress(100);
        unit2.setCoils(List.of(false, true, true, false));
        unit2.setHoldingStartAddress(1000);
        unit2.setHoldingRegisters(List.of(65535, 1234, 4321, 0, 32768));

        config.setDevices(List.of(unit1, unit2));
        server = new ModbusTcpServer(config);
        server.start();
        port = server.boundAddress().getPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    @Test
    void supportsSplitFramesPipelinedFramesExceptionsAndContinuedRequests() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream();
            DataInputStream input = new DataInputStream(socket.getInputStream());

            byte[] readCoils = frame(10, 1, new byte[]{0x01, 0x00, 0x00, 0x00, 0x08});
            output.write(readCoils, 0, 4);
            Thread.sleep(20);
            output.write(readCoils, 4, readCoils.length - 4);
            assertArrayEquals(new byte[]{
                    0x00, 0x0A, 0x00, 0x00, 0x00, 0x04, 0x01, 0x01, 0x01, 0x65
            }, readFrame(input));

            byte[] readRegisters = frame(11, 1, new byte[]{0x03, 0x00, 0x00, 0x00, 0x02});
            byte[] badUnit = frame(12, 9, new byte[]{0x03, 0x00, 0x00, 0x00, 0x01});
            byte[] badFunction = frame(13, 1, new byte[]{0x08, 0x00, 0x00, 0x00, 0x01});
            byte[] badAddress = frame(14, 1, new byte[]{0x03, 0x60, 0x00, 0x00, 0x01});
            byte[] badQuantity = frame(15, 1, new byte[]{0x03, 0x00, 0x00, 0x00, 0x00});
            byte[] unit2Read = frame(16, 2, new byte[]{0x03, 0x03, (byte) 0xE8, 0x00, 0x03});
            output.write(concat(readRegisters, badUnit, badFunction, badAddress, badQuantity, unit2Read));

            assertArrayEquals(new byte[]{
                    0x00, 0x0B, 0x00, 0x00, 0x00, 0x07, 0x01, 0x03, 0x04,
                    0x00, 0x64, 0x00, (byte) 0xC8
            }, readFrame(input));
            assertArrayEquals(new byte[]{0x00, 0x0C, 0x00, 0x00, 0x00, 0x03, 0x09, (byte) 0x83, 0x0B}, readFrame(input));
            assertArrayEquals(new byte[]{0x00, 0x0D, 0x00, 0x00, 0x00, 0x03, 0x01, (byte) 0x88, 0x01}, readFrame(input));
            assertArrayEquals(new byte[]{0x00, 0x0E, 0x00, 0x00, 0x00, 0x03, 0x01, (byte) 0x83, 0x02}, readFrame(input));
            assertArrayEquals(new byte[]{0x00, 0x0F, 0x00, 0x00, 0x00, 0x03, 0x01, (byte) 0x83, 0x03}, readFrame(input));
            assertArrayEquals(new byte[]{
                    0x00, 0x10, 0x00, 0x00, 0x00, 0x09, 0x02, 0x03, 0x06,
                    (byte) 0xFF, (byte) 0xFF, 0x04, (byte) 0xD2, 0x10, (byte) 0xE1
            }, readFrame(input));
        }
    }

    @Test
    void validatesSingleCoilValue() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            output.write(frame(1, 1, new byte[]{0x05, 0x00, 0x00, 0x01, 0x00}));
            assertArrayEquals(new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01, (byte) 0x85, 0x03}, readFrame(input));
        }
    }

    @Test
    void failedBatchWriteChangesNothing() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream();
            DataInputStream input = new DataInputStream(socket.getInputStream());

            output.write(frame(1, 1, new byte[]{
                    0x10, 0x00, 0x0C, 0x00, 0x09, 0x12,
                    1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8, 0, 9, 0,
            }));
            assertArrayEquals(new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01, (byte) 0x90, 0x02}, readFrame(input));

            output.write(frame(2, 1, new byte[]{0x03, 0x00, 0x08, 0x00, 0x08}));
            assertArrayEquals(new byte[]{
                    0x00, 0x02, 0x00, 0x00, 0x00, 0x13, 0x01, 0x03, 0x10,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            }, readFrame(input));
        }
    }

    @Test
    void concurrentBatchWritesAreAtomic() throws Exception {
        int writers = 8;
        List<Thread> threads = new ArrayList<>();
        for (int writer = 0; writer < writers; writer++) {
            int pattern = writer + 1;
            Thread thread = new Thread(() -> {
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    OutputStream output = socket.getOutputStream();
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    byte[] pdu = new byte[]{0x10, 0x00, 0x00, 0x00, 0x08, 0x10,
                            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                    for (int i = 0; i < 8; i++) {
                        pdu[6 + i * 2 + 1] = (byte) pattern;
                    }
                    output.write(frame(pattern, 1, pdu));
                    byte[] response = readFrame(input);
                    assertEquals(0x10, response[7] & 0xFF);
                } catch (Exception ignored) {
                }
            });
            threads.add(thread);
        }
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }

        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            output.write(frame(100, 1, new byte[]{0x03, 0x00, 0x00, 0x00, 0x08}));
            byte[] response = readFrame(input);
            int pattern = response[10] & 0xFF;
            assertTrue(pattern >= 1 && pattern <= writers);
            for (int i = 0; i < 8; i++) {
                assertEquals(pattern, response[9 + i * 2 + 1] & 0xFF);
            }
        }
    }

    @Test
    void closesConnectionWhenProtocolIdentifierIsInvalid() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.getOutputStream().write(new byte[]{
                    0x00, 0x01, 0x00, 0x01, 0x00, 0x06, 0x01, 0x03, 0x00, 0x00, 0x00, 0x01
            });
            socket.getOutputStream().flush();
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    private static byte[] frame(int transactionId, int unitId, byte[] pdu) {
        byte[] frame = new byte[7 + pdu.length];
        frame[0] = (byte) (transactionId >> 8);
        frame[1] = (byte) transactionId;
        frame[4] = (byte) ((pdu.length + 1) >> 8);
        frame[5] = (byte) (pdu.length + 1);
        frame[6] = (byte) unitId;
        System.arraycopy(pdu, 0, frame, 7, pdu.length);
        return frame;
    }

    private static byte[] readFrame(DataInputStream input) throws Exception {
        int transactionId = input.readUnsignedShort();
        input.readUnsignedShort();
        int length = input.readUnsignedShort();
        byte[] frame = new byte[6 + length];
        frame[0] = (byte) (transactionId >> 8);
        frame[1] = (byte) transactionId;
        frame[4] = (byte) (length >> 8);
        frame[5] = (byte) length;
        input.readFully(frame, 6, length);
        return frame;
    }

    private static byte[] concat(byte[]... frames) {
        int total = 0;
        for (byte[] frame : frames) {
            total += frame.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] frame : frames) {
            System.arraycopy(frame, 0, result, offset, frame.length);
            offset += frame.length;
        }
        return result;
    }
}
